package wt;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.List;

import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.Threads;
import spim.process.fusion.export.DisplayImage;
import wt.tools.Mirror;

public class Alignment
{
	final DisplayImage disp = new DisplayImage();
	
	public Alignment( final Img< FloatType > template, final Img< FloatType > wing, final Img< FloatType > wingGene ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// find the initial alignment
		final InitialTransform transform1 = new InitialTransform( template, wing );

		if ( !transform1.findInitialModel() )
			return;

		if ( transform1.mirror() )
		{
			IJ.log( "Mirroring wing" );
			Mirror.mirror( wing, 0, Threads.numThreads() );
			Mirror.mirror( wingGene, 0, Threads.numThreads() );
		}

		// preprocess and transform
		long offset;

		final Preprocess pTemplate = new Preprocess( template );
		pTemplate.homogenize();
		offset = pTemplate.extend( 0.2f, true );

		final Preprocess pWing = new Preprocess( wing );
		pWing.homogenize();
		pWing.extend( 0.2f, true );

		// transform pWing
		final List< PointMatch > matches = transform1.createUpdatedMatches( Util.getArrayFromValue( offset, wing.numDimensions() ) );
		final AffineModel2D model = new AffineModel2D();
		model.fit( matches );
		pWing.transform( model );

		// compute non-rigid alignment
		new NonrigidAlignment( pTemplate.output, pWing.output );

		//disp.exportImage( pTemplate.output, "homogenized template" );
		//disp.exportImage( pWing.output, "homogenized wing" );
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

	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
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
