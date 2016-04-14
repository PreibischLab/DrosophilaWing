package wt.alignment;
import java.util.ArrayList;
import mpicbg.models.AffineModel2D;
import mpicbg.models.NoninvertibleModelException;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.morphology.BlackTopHat;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;
import net.imglib2.algorithm.stats.ComputeMinMax;

public class Preprocess
{
	final Img< FloatType > input;
	Img< FloatType > output;
	double avgBorderValue1 = Double.NaN;

	/**
	 * Extend the image and homogenize the contrast for proper alignment
	 * @param img
	 */
	public Preprocess( final Img< FloatType > img )
	{
		this.input = img;
	}

	public Img< FloatType > input()  { return input; }
	public Img< FloatType > output() { return output; }
	
	public void homogenize()
	{
			homogenizeBothat(10);
		//homogenizeGauss();
	}
	
	public void homogenizeBothat(long radius)
	{
		// this.output = input.factory().create( input, input.firstElement() );
		HyperSphereShape strel = new HyperSphereShape(radius);
		this.output = BlackTopHat.blackTopHat(input, strel, Runtime.getRuntime().availableProcessors());
		
		Img< FloatType >  tmpGauss = output.factory().create( output, output.firstElement() );
		
		try {
			Gauss3.gauss( 8, Views.extendBorder( this.output ), tmpGauss);
		} catch (IncompatibleTypeException e) {}
		
		final Cursor< FloatType > c1 = output.cursor();
		final Cursor< FloatType > c2 = tmpGauss.cursor();
		while ( c1.hasNext() ) {
			final float bhat = c1.next().get();
			final float gau = c2.next().get();
			c1.get().setReal( gau + bhat);
		}
		
		// Compute min and max
		FloatType min = new FloatType();
		FloatType max = new FloatType();
		final ComputeMinMax<FloatType> cmm = new ComputeMinMax<FloatType>(output, min, max);
		if (!cmm.checkInput() || !cmm.process()) {
			try {
				throw new Exception("Coult not compute min and max: " + cmm.getErrorMessage());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// If min and max are the same, we just return the empty image will all zeros
		if (0 == cmm.getMin().compareTo(cmm.getMax())) {
			
		}
		min = cmm.getMin();
		max = cmm.getMax();

		FloatType diff = new FloatType();
		diff.set(max.get()-min.get());
		// Normalize in place the target image
		final Cursor< FloatType > c = output.cursor();
		while ( c.hasNext() ) {
			final float in = c.next().get();
			c.get().setReal(255 * (max.get() - in) / diff.get() );
		}
		
		// display image
		/*
		final ImageStack stack = new ImageStack( (int)output.dimension( 0 ), (int)output.dimension( 1 ) );
		stack.addSlice( ImageTools.wrap( output ) );
		new ImagePlus( "blackTopHat", stack ).show();
		*/
	}
	
	public void homogenizeGauss()
	{
		this.output = input.factory().create( input, input.firstElement() );

		try
		{
			Gauss3.gauss( 40, Views.extendBorder( input ), this.output );
		}
		catch (IncompatibleTypeException e) {}

		final Cursor< FloatType > c1 = input.cursor();
		final Cursor< FloatType > c2 = output.cursor();

		while ( c1.hasNext() )
		{
			final float in = c1.next().get();
			final float out = c2.next().get();

			c2.get().set( in / out );
		}
	}

	public long extend( final float fraction, final boolean simple )
	{
		long size = 0;

		for ( int d = 0; d < output.numDimensions(); ++d )
			size = Math.max( size, Math.round( output.dimension( d ) * fraction ) );

		final long[] offset = new long[ output.numDimensions() ];
		final long[] newDim = new long[ output.numDimensions() ];

		for ( int d = 0; d < newDim.length; ++d )
		{
			offset[ d ] = size;
			newDim[ d ] = output.dimension( d ) + 2 * size;
		}

		final Img< FloatType > outExtended = output.factory().create( newDim, output.firstElement() );

		final Cursor< FloatType > c = outExtended.localizingCursor();

		final FloatType avg = new FloatType( (float)border() );

		if ( simple )
		{
			final RandomAccess< FloatType > r = Views.translate( Views.extendValue( output, avg ), offset ).randomAccess();

			while ( c.hasNext() )
			{
				c.fwd();
				r.setPosition( c );
				c.get().set( r.get() );
			}
		}
		else
		{
			final RandomAccess< FloatType > r = Views.translate( Views.extendBorder( output ), offset ).randomAccess();

			while ( c.hasNext() )
			{
				c.fwd();
				r.setPosition( c );
	
				if ( r.getIntPosition( 0 ) < size || r.getIntPosition( 1 ) < size ||
					r.getIntPosition( 0 ) >= output.dimension( 0 ) + size || r.getIntPosition( 1 ) >= output.dimension( 1 ) + size )
				{
					c.get().set( r.get() );
				}
				else
				{
					c.get().set( avg );
				}
			}
	
			try
			{
				Gauss3.gauss( 10, Views.extendValue( outExtended, avg ), outExtended );
			}
			catch (IncompatibleTypeException e) {}
	
			c.reset();
			while ( c.hasNext() )
			{
				c.fwd();
				r.setPosition( c );
	
				if (!( r.getIntPosition( 0 ) < size || r.getIntPosition( 1 ) < size ||
					r.getIntPosition( 0 ) >= output.dimension( 0 ) + size || r.getIntPosition( 1 ) >= output.dimension( 1 ) + size ))
				{
					c.get().set( r.get() );
				}
			}
		}

		output = outExtended;

		return size;
	}

	public void transform( final AffineModel2D model )
	{
		final Img< FloatType > outT = output.factory().create( this.output, this.output.firstElement() );

		final Cursor< FloatType > cursor = outT.localizingCursor();
		final double borderValue = border();

		final RealRandomAccess< FloatType > interpolate =
				Views.interpolate( Views.extendValue( output, new FloatType( (float)borderValue ) ),
						new NLinearInterpolatorFactory< FloatType >() ).realRandomAccess();

		final double[] tmp = new double[ outT.numDimensions() ];

		try
		{

			while ( cursor.hasNext() )
			{
				final FloatType t = cursor.next();
				cursor.localize( tmp );
				model.applyInverseInPlace( tmp );
				interpolate.setPosition( tmp );
				t.set( interpolate.get() );
			}
		} 
		catch (NoninvertibleModelException e) { e.printStackTrace(); }

		this.output = outT;
	}

	protected double border()
	{
		if ( !Double.isNaN( this.avgBorderValue1 ) )
			return this.avgBorderValue1;

		// compute the average border intensity
		// strictly 2d!
		final ArrayList< Coordinates > borderPixelList = 
				new ArrayList< Coordinates >((int) (2*output.dimension( 0 ) + 2*output.dimension( 1 ) - 4));
		int row=0,col=0;
		do {
			borderPixelList.add(new Coordinates(col,row));
			row++;
		} while ( row < output.dimension( 0 ) - 1 );

		do {
			col++;
			borderPixelList.add(new Coordinates(col,row));
		} while ( col < output.dimension( 1 ) - 1 );

		do {
			row--;
			borderPixelList.add(new Coordinates(col,row));
		} while ( row > 0 );

		do {
			col--;
			borderPixelList.add(new Coordinates(col,row));
		} while ( col > 0 );
		
		this.avgBorderValue1 = Median.median(output, borderPixelList).getRealDouble();

		return avgBorderValue1;
	}
	
	protected double borderAverage()
	{
		if ( !Double.isNaN( this.avgBorderValue1 ) )
			return this.avgBorderValue1;

		// compute the average border intensity
		// strictly 2d!
		final RandomAccess< FloatType > r = output.randomAccess();

		r.setPosition( 0, 0 );
		r.setPosition( 0, 1 );

		final RealSum avg = new RealSum();
		int count = 0;

		do
		{
			r.fwd( 0 );
			avg.add( r.get().get() );
			++count;
		} while ( r.getIntPosition( 0 ) < output.dimension( 0 ) - 1 );

		do
		{
			r.fwd( 1 );
			avg.add( r.get().get() );
			++count;
		} while ( r.getIntPosition( 1 ) < output.dimension( 1 ) - 1 );

		do
		{
			r.bck( 0 );
			avg.add( r.get().get() );
			++count;
		} while ( r.getIntPosition( 0 ) > 0 );

		do
		{
			r.bck( 1 );
			avg.add( r.get().get() );
			++count;
		} while ( r.getIntPosition( 1 ) > 0 );

		this.avgBorderValue1 = avg.getSum() / (double)count;

		return avgBorderValue1;
	}
	
	public double avgImage( final Img< FloatType > img )
	{
		RealSum sum = new RealSum();

		for ( final FloatType t : img )
			sum.add( t.get() );

		return sum.getSum() / (double)img.size();
	}

}
