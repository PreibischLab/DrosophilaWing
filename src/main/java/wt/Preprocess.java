package wt;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;

public class Preprocess
{
	final Img< FloatType > input;
	Img< FloatType > output;

	/**
	 * Extend the image and homogenize the contrast for proper alignment
	 * @param img
	 */
	public Preprocess( final Img< FloatType > img )
	{
		this.input = img;
	}

	public Img< FloatType > input() { return input; }
	public Img< FloatType > output() { return output; }

	public void homogenize()
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

	public double border()
	{
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

		return avg.getSum() / (double)count;
	}

	public double avgImage( final Img< FloatType > img )
	{
		RealSum sum = new RealSum();

		for ( final FloatType t : img )
			sum.add( t.get() );

		return sum.getSum() / (double)img.size();
	}
}
