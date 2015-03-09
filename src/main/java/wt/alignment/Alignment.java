package wt.alignment;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.io.File;

import mpicbg.models.AbstractAffineModel2D;
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
	protected final InitialTransform transform1;
	protected ImagePlus aligned = null;
	protected boolean mirror;
	protected long[] offset;
	protected AbstractAffineModel2D< ? > model;
	protected Transformation t;
	protected int subsampling;

	public Alignment( final Img< FloatType > template, final Img< FloatType > wing, final Img< FloatType > wingGene ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// find the initial alignment
		this.transform1 = new InitialTransform( template, wing );

		if ( !transform1.findInitialModel() )
			return;

		if ( transform1.mirror() )
		{
			System.out.println( "Mirroring wing" );
			Mirror.mirror( wing, 0, Threads.numThreads() );
			Mirror.mirror( wingGene, 0, Threads.numThreads() );
		}

		this.mirror = transform1.mirror();
		this.model = transform1.model();

		// preprocess and transform
		final Preprocess pTemplate = new Preprocess( template );
		pTemplate.homogenize();
		this.offset = Util.getArrayFromValue( pTemplate.extend( 0.2f, true ), wing.numDimensions() );

		final Preprocess pWing = new Preprocess( wing );
		pWing.homogenize();
		pWing.extend( 0.2f, true );

		// transform pWing
		final AffineModel2D model = new AffineModel2D();
		model.fit( transform1.createUpdatedMatches( this.offset ) );
		pWing.transform( model );

		// compute non-rigid alignment
		this.subsampling = 2;
		this.t = NonrigidAlignment.align( pWing.output, pTemplate.output, this.subsampling );

		// transform the original images
		final Img< FloatType > wingAligned = NonrigidAlignment.transformAll( wing, this.model, this.offset, this.t, this.subsampling );
		final Img< FloatType > wingGeneAligned = NonrigidAlignment.transformAll( wingGene, this.model, this.offset, this.t, this.subsampling );

		this.aligned = overlay( wingAligned, wingGeneAligned );
	}

	public boolean saveTransform( final String log, final File file ) { return LoadSaveTransformation.save( log + "\n" + transform1.log, mirror, offset, model, t, subsampling, file ); }
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
		final File wingSavedLog = new File( wingFile.getAbsolutePath().substring( 0, wingFile.getAbsolutePath().length() - 4 ) + ".aligned.txt" );
		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );
		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final Img< FloatType > template = convert( templateImp, 0 );
		final Img< FloatType > wing = convert( wingImp, 0 );
		final Img< FloatType > wingGene = convert( wingImp, 1 );

		final Alignment alignment = new Alignment( template, wing, wingGene );

		final ImagePlus aligned = alignment.getAlignedImage();
		//new FileSaver( aligned ).saveAsZip( wingSavedFile.getAbsolutePath() );
		aligned.show();
		alignment.saveTransform( "transformed image '" + wingSavedFile.getAbsolutePath() + "'", wingSavedLog );
	}
}
