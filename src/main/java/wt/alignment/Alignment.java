package wt.alignment;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;

import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.Threads;
import wt.tools.Mirror;
import bunwarpj.Transformation;

public class Alignment
{
	public ImagePlus aligned = null;

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
		final AffineModel2D model = new AffineModel2D();
		model.fit( transform1.createUpdatedMatches( Util.getArrayFromValue( offset, wing.numDimensions() ) ) );
		pWing.transform( model );

		// compute non-rigid alignment
		final int subSampling = 2;
		final Transformation t = NonrigidAlignment.align( pWing.output, pTemplate.output, subSampling );

		// transform the original images
		final Img< FloatType > wingAligned = NonrigidAlignment.transformAll( wing, transform1.model(), Util.getArrayFromValue( offset, wing.numDimensions() ), t, subSampling );
		final Img< FloatType > wingGeneAligned = NonrigidAlignment.transformAll( wingGene, transform1.model(), Util.getArrayFromValue( offset, wing.numDimensions() ), t, subSampling );

		this.aligned = overlay( wingAligned, wingGeneAligned );
	}

	public ImagePlus getAlignedImage() { return aligned; }

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

	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		new ImageJ();

		final File templateFile = new File( "wing_template_A13_2014_01_31.tif" );
		final File wingFile = new File( "A12_002.tif" );
		final File wingSavedFile = new File( wingFile.getAbsolutePath().substring( 0, wingFile.getAbsolutePath().length() - 4 ) + ".aligned.zip" );
		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );
		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final Img< FloatType > template = convert( templateImp, 0 );
		final Img< FloatType > wing = convert( wingImp, 0 );
		final Img< FloatType > wingGene = convert( wingImp, 1 );

		final Alignment alignment = new Alignment( template, wing, wingGene );

		final ImagePlus aligned = alignment.getAlignedImage();
		new FileSaver( aligned ).saveAsZip( wingSavedFile.getAbsolutePath() );
	}
}
