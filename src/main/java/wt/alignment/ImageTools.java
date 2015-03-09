package wt.alignment;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;

public class ImageTools
{
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
