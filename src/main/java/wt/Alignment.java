package wt;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;

import spim.process.fusion.export.DisplayImage;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

public class Alignment
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

	public static void main( String args[] )
	{
		final File templateFile = new File( "wing_template_A13_2014_01_31.tif" );
		final File wingFile = new File( "A12_002.tif" );

		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );
		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		//templateImp.show();
		//wingImp.show();

		final Img< FloatType > template = convert( templateImp, 0 );
		final Img< FloatType > wing = convert( wingImp, 0 );
		final Img< FloatType > wingGene = convert( wingImp, 1 );

		final DisplayImage disp = new DisplayImage();

		disp.exportImage( template, "Template" );
		disp.exportImage( wing, "wing" );
		disp.exportImage( wingGene, "wingGene" );
	}
}
