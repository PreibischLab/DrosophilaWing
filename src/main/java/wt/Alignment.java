package wt;

import ij.ImageJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;

import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.export.DisplayImage;

public class Alignment
{
	public Alignment( final Img< FloatType > template, final Img< FloatType > wing, final Img< FloatType > wingGene )
	{
		final DisplayImage disp = new DisplayImage();

		//disp.exportImage( template, "Template" );
		//disp.exportImage( wing, "wing" );
		//disp.exportImage( wingGene, "wingGene" );

		final Preprocess pTemplate = new Preprocess( template );
		pTemplate.homogenize();
		pTemplate.extend( 0.2f, true );

		final Preprocess pWing = new Preprocess( wing );
		pWing.homogenize();
		pWing.extend( 0.2f, true );

		disp.exportImage( pTemplate.output, "homogenized template" );
		disp.exportImage( pWing.output, "homogenized wing" );
	}

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
		new ImageJ();

		final File templateFile = new File( "wing_template_A13_2014_01_31.tif" );
		final File wingFile = new File( "A12_002.tif" );

		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );
		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final Img< FloatType > template = convert( templateImp, 0 );
		final Img< FloatType > wing = convert( wingImp, 0 );
		final Img< FloatType > wingGene = convert( wingImp, 1 );

		new Alignment( template, wing, wingGene );
	}
}
