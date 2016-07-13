package wt.alignment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
// import mpicbg.models.IllDefinedDataPointsException;
// import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.ValuePair;

public class ImageTools
{	
	static public class loadImage
	{
		private boolean isSwitched = false;
		private ImagePlus overlay = null;
		private Pair< File, File > file;
		private String bfName = null;
		private String geName = null;
		 
		ImagePlus          getImage()           {return overlay;}
		Pair< File, File > getPair()            {return file;}
		boolean            getSwitched()        {return isSwitched;}
		String             getBrightFieldName() {return bfName; }
		String             getGeneName()        {return geName; }
		
		public loadImage( final Pair< File, File > _file )
		{
			file = _file;
			bfName = file.getA().getName();
			if (file.getB() == null || file.getB().isDirectory())
			{
				File filepart = file.getA();
				System.out.println( "Opening: " + filepart.getAbsolutePath() );
	
				final ImagePlus imp = new ImagePlus( filepart.getAbsolutePath() );
	
				if ( imp.getStack().getSize() != 2 )
				{
					System.out.println( "Image '" + filepart.getAbsolutePath() + "' is expected to have two slices. Stopping." );
					return;
				}
				geName = file.getA().getName();
				overlay = imp; //TODO: use the sort function
			}
			else
			{
				if (!file.getA().exists() || !file.getB().exists() ){
					System.out.println( "Image file could not be read. Stopping." );
					return;
				}
		
				System.out.println( "Opening: " + file.getA().getAbsolutePath() );
				System.out.println( "         " + file.getB().getAbsolutePath() );
				final Img< FloatType > img1 = convert( new ImagePlus( file.getA().getAbsolutePath() ), 0 );
				final Img< FloatType > img2 = convert( new ImagePlus( file.getB().getAbsolutePath() ), 0 );
				
				geName = file.getB().getName();
				overlay = sortBrightFieldFirst(img1, img2);
			}
		}
		
		private ImagePlus sortBrightFieldFirst(Img< FloatType > img1, Img< FloatType > img2)
		{
			final double avg1 = avg( img1 );
			final double avg2 = avg( img2 );

			// the brighter one first
			if ( avg1 > avg2 )
				return overlay( img1, img2 );
			else 
			{
				System.out.println( "Brightfield and expression image seems to be switched. Correcting." );
				Pair< File, File > pair = new ValuePair< File, File >(file.getB(), file.getA() );
				file = pair;
				isSwitched = true;
				return overlay( img2, img1 );
			}
		}
	}
	
	public static final double avg( final Img< FloatType > img )
	{
		final RealSum sum = new RealSum();

		for ( final FloatType t : img )
			sum.add( t.getRealDouble() );

		return sum.getSum() / (double)img.size();
	}
	
	/**
	 * Convert to float the z page of the ImagePlus
	 * @param imp - input image ImagePlus
	 * @param z - the image directory
	 */
	public static Img< FloatType > convert( final ImagePlus imp, final int z )
	{
		final ImageProcessor ip = imp.getStack().getProcessor( z + 1 );

		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight() };

		if ( FloatProcessor.class.isInstance( ip ) )
		{
			return ArrayImgs.floats( (float[])((FloatProcessor)ip).getPixels(), dim );
		}
		else
		{
			final Img< FloatType > img = ArrayImgs.floats( dim );

			int i = 0;

			for ( final FloatType t : img )
				t.set( ip.getf( i++ ) );

			return img;
		}
	}

	public static class Median
	{
		final Img< FloatType > img;
		final ArrayList< Point > pixelList;
		
		public Median(final Img< FloatType > _img)
		{
			this.img = _img;
			pixelList = new ArrayList< Point >();
			int row=0;
			do {
				int col=0;
				do {
					pixelList.add(new Point(row, col));
					col++;
				} while ( col < img.dimension( 1 ) - 1 );
				row++;
			} while ( row < img.dimension( 0 ) - 1 );
			
			Collections.sort( pixelList, new PointComparator< FloatType >( img ) );
		}
		
		public Median(final Img< FloatType > _img, final ArrayList< Point > _pixelList)
		{
			this.img = _img;
			this.pixelList = new ArrayList<Point>(_pixelList);
			Collections.sort( pixelList, new PointComparator< FloatType >( img ) );
		}

		public float get()
		{
			return get((float)0.5);
		}
		
		public float get(double percentage)
		{
			return get((float)percentage);
		}
		
		public float get(float percentage)
		{
			if (percentage>1) percentage = 1;
			if (percentage<0) percentage = 0;

			RandomAccess< FloatType  > r = img.randomAccess();

			float med = 0;
			float pos = (pixelList.size()-1) * percentage;
			int posInt = (int)  (float) Math.floor(pos);
			float rem  = pos - posInt;
			if (rem == 0)
			{
				r.setPosition( pixelList.get( posInt ) );
				med = r.get().getRealFloat();
			}
			else {
				r.setPosition( pixelList.get( posInt ) );
				med += r.get().getRealFloat() * (1-rem);

				r.setPosition( pixelList.get( posInt + 1 ) );
				med += r.get().getRealFloat() * rem;
			}
			return med;
		}
	}

	public static ArrayList< Point > borders( Img< FloatType > img , int width)
	{
		final ArrayList< Point > borderPixelList = 
		new ArrayList< Point >();
		
		// Left (full)
		for (int col = 0; col < width ;col++ )
			for (int row = 0 ; row < img.dimension( 0 ) ; row++)
				borderPixelList.add(new Point(row, col));
		
		// right (full)
		for (int col = (int)img.dimension(1)-width+1 ; col < img.dimension( 1 ) ; col++ )
			for (int row = 0 ; row < img.dimension( 0 ) ; row++)
				borderPixelList.add(new Point(row, col));
		
		// top
		for (int row = 0; row < width ;row++ )
			for (int col = width ; col < img.dimension( 1 )-width+1 ; col++)
				borderPixelList.add(new Point(row, col));
		
		
		// bottom
		for (int row = (int) (img.dimension( 0 )-width+1) ; row < img.dimension( 0 ) ;row++ )
			for (int col = width ; col < img.dimension( 1 )-width+1 ; col++)
				borderPixelList.add(new Point(row, col));
		
		return borderPixelList;
	}
	@SuppressWarnings("unchecked")
	public static FloatProcessor wrap( final Img< FloatType > img )
	{
		if ( !ArrayImg.class.isInstance( img ) )
			throw new RuntimeException( "Only ArrayImg is supported" );

		if ( img.numDimensions() != 2 )
			throw new RuntimeException( "Only 2d is supported" );

		final float[] array = ((ArrayImg< FloatType, FloatArray >)img).update( null ).getCurrentStorageArray();

		return new FloatProcessor( (int)img.dimension( 0 ), (int)img.dimension( 1 ), array );
	}

	public static ImagePlus overlay( final Img< FloatType > img1, final Img< FloatType > img2 )
	{
		final ImageStack stack = new ImageStack( (int)img1.dimension( 0 ), (int)img1.dimension( 1 ) );

		stack.addSlice( wrap( img1 ) );
		stack.addSlice( wrap( img2 ) );

		return new ImagePlus( "Overlay", stack );
	}

	public static ImagePlus overlay( final Img< FloatType > img1, final Img< FloatType > img2, final Img< FloatType > img3 )
	{
		final ImageStack stack = new ImageStack( (int)img1.dimension( 0 ), (int)img1.dimension( 1 ) );

		stack.addSlice( wrap( img1 ) );
		stack.addSlice( wrap( img2 ) );
		stack.addSlice( wrap( img3 ) );

		return new ImagePlus( "Overlay", stack );
	}

	
}
