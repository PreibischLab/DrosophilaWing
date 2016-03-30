package wt.alignment;

import java.io.File;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.RealSum;

public class ImageTools
{
	public static ImagePlus loadImage( final File filepart )
	{
		if ( filepart.exists() )
		{
			System.out.println( "Opening: " + filepart.getAbsolutePath() );

			final ImagePlus imp = new ImagePlus( filepart.getAbsolutePath() );
	
			if ( imp.getStack().getSize() != 2 )
			{
				System.out.println( "Image '" + filepart.getAbsolutePath() + "' does not have two slices. Stopping." );
				return null;
			}

			return imp;
		}
		else
		{
			File dir = new File( filepart.getParent() );
			String fileStart = filepart.getName();

			if ( !dir.exists() )
			{
				System.out.println( "Dir '" + dir.getAbsolutePath() + "' does not exist. Stopping." );
				return null;
			}

			if ( !dir.isDirectory() )
			{
				System.out.println( "Dir '" + dir.getAbsolutePath() + "' is not a directory. Stopping." );
				return null;
			}

			final String[] files = dir.list();
			final ArrayList< File > images = new ArrayList< File >();

			for ( final String f : files )
			{
				if ( f.startsWith( fileStart ) && !f.endsWith( ".txt") && !f.endsWith( ".zip" ) )
				{
					final File imgFile = new File( dir, f );

					if ( imgFile.exists() )
						images.add( imgFile );
				}
			}

			System.out.println( "Files found: " );

			for ( final File imgFile : images )
				System.out.println( imgFile );

			if ( images.size() != 2 )
			{
				System.out.println( "These are not two files (one brighfield, one gene expression). Stopping." );
				return null;
			}

			final Img< FloatType > img1 = convert( new ImagePlus( images.get( 0 ).getAbsolutePath() ), 0 );
			final Img< FloatType > img2 = convert( new ImagePlus( images.get( 1 ).getAbsolutePath() ), 0 );

			final double avg1 = avg( img1 );
			final double avg2 = avg( img2 );

			// the brighter one first
			if ( avg1 > avg2 )
				return overlay( img1, img2 );
			else
				return overlay( img2, img1 );
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
