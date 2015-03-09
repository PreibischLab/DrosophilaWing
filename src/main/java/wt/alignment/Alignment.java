package wt.alignment;

import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;

import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.multithreading.SimpleMultiThreading;
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

		this.aligned = ImageTools.overlay( wingAligned, wingGeneAligned );
	}

	public boolean saveTransform( final String log, final File file ) { return LoadSaveTransformation.save( log + "\n" + transform1.log, mirror, offset, model, t, subsampling, file ); }
	public ImagePlus getAlignedImage() { return aligned; }

	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		new ImageJ();

		final File templateFile = new File( "wing_template_A13_2014_01_31.tif" );

		//final File wingFile = new File( "A12_002.tif" );
		final File wingFile = new File( "/Users/preibischs/Downloads/samples/B16/wing_B16_dsRed_001" );

		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );
		final ImagePlus wingImp = ImageTools.loadImage( wingFile );
		final File wingSavedFile = new File( wingFile.getAbsolutePath().substring( 0, wingFile.getAbsolutePath().length() - 4 ) + ".aligned.zip" );
		final File wingSavedLog = new File( wingFile.getAbsolutePath().substring( 0, wingFile.getAbsolutePath().length() - 4 ) + ".aligned.txt" );

		final Img< FloatType > template = ImageTools.convert( templateImp, 0 );
		final Img< FloatType > wing = ImageTools.convert( wingImp, 0 );
		final Img< FloatType > wingGene = ImageTools.convert( wingImp, 1 );

		final Alignment alignment = new Alignment( template, wing, wingGene );

		final ImagePlus aligned = alignment.getAlignedImage();
		//new FileSaver( aligned ).saveAsZip( wingSavedFile.getAbsolutePath() );
		aligned.show();
		alignment.saveTransform( "transformed image '" + wingSavedFile.getAbsolutePath() + "'", wingSavedLog );
	}
}
