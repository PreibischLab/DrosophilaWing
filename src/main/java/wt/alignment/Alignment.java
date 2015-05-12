package wt.alignment;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.io.File;

import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.Threads;
import wt.tools.Mirror;
import bunwarpj.Transformation;

public class Alignment
{
	protected final InitialTransform transform1;
	protected final NonrigidAlignment nra;
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
		this.nra = new NonrigidAlignment();

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

		//new ImagePlus( "template", ImageTools.wrap( pTemplate.output ) ).show();;
		//new ImagePlus( "wing", ImageTools.wrap( pWing.output ) ).show();;

		// compute non-rigid alignment
		this.subsampling = 2;
		this.t = nra.align( pWing.output, pTemplate.output, pWing.border(), pTemplate.border(), this.subsampling );

		// transform the original images
		final Img< FloatType > wingAligned = nra.transformAll( wing, this.model, this.offset, this.t, this.subsampling );
		final Img< FloatType > wingGeneAligned = nra.transformAll( wingGene, this.model, this.offset, this.t, this.subsampling );

		this.aligned = ImageTools.overlay( template, wingAligned, wingGeneAligned );

		//this.aligned.show();
		//SimpleMultiThreading.threadHaltUnClean();
	}

	public boolean saveTransform( final String log, final File file )
	{
		return LoadSaveTransformation.save( log + "\n" + transform1.log() +  "\n" + nra.log(), mirror, offset, model, t, subsampling, file );
	}

	public ImagePlus getAlignedImage() { return aligned; }

	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		new ImageJ();

		final File templateFile = new File( "wing_template_A13_2014_01_31.tif" );
		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );

		final ImageStack stack = new ImageStack( templateImp.getWidth(), templateImp.getHeight() );

		//final File wingFile = new File( "A12_002.tif" );

		for ( int i = 1; i <= 37; ++i )
		{
			final File wingFile;
			if ( i < 10 )
				wingFile = new File( "/media/preibisch/data/Microscopy/Drosophila Wing Gompel/samples/A2/wing_A2_dsRed_00" + i );
			else
				wingFile = new File( "/media/preibisch/data/Microscopy/Drosophila Wing Gompel/samples/A2/wing_A2_dsRed_0" + i );
	
			final File wingSavedFile = new File( wingFile.getAbsolutePath().substring( 0, wingFile.getAbsolutePath().length() ) + ".aligned.zip" );
			final File wingSavedLog = new File( wingFile.getAbsolutePath().substring( 0, wingFile.getAbsolutePath().length() ) + ".aligned.txt" );

			/*
			if ( !wingSavedFile.exists() )
				continue;

			final ImagePlus img = new ImagePlus( wingSavedFile.getAbsolutePath() );
			stack.addSlice( wingFile.getName(), img.getStack().getProcessor( 2 ) );
			*/

			final ImagePlus wingImp = ImageTools.loadImage( wingFile );

			if ( wingImp == null )
				continue;

			final Img< FloatType > template = ImageTools.convert( templateImp, 0 );
			final Img< FloatType > wing = ImageTools.convert( wingImp, 0 );
			final Img< FloatType > wingGene = ImageTools.convert( wingImp, 1 );
	
			final Alignment alignment = new Alignment( template, wing, wingGene );
	
			final ImagePlus aligned = alignment.getAlignedImage();
			if ( aligned != null )
			{
				stack.addSlice( wingFile.getName(), aligned.getStack().getProcessor( 2 ) );
				new FileSaver( aligned ).saveAsZip( wingSavedFile.getAbsolutePath() );
	
				alignment.saveTransform( "transformed image '" + wingSavedFile.getAbsolutePath() + "'", wingSavedLog );
			}
			else
			{
				stack.addSlice( wingFile.getName(), wing );
			}
		}
		
		new ImagePlus( "all", stack ).show();
	}
}
