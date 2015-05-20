package wt.alignment;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.io.File;
import java.util.List;

import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import spim.Threads;
import wt.tools.CommonFileName;
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

	public Alignment( final Img< FloatType > template, final Img< FloatType > wing, final Img< FloatType > wingGene, final double imageWeight ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// find the initial alignment
		this.transform1 = new InitialTransform( template, wing );
		this.nra = new NonrigidAlignment();

		if ( !transform1.findInitialModel() )
			return;

		if ( transform1.mirror() )
		{
			IJ.log( "Mirroring wing" );
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
		this.t = nra.align( pWing.output, pTemplate.output, pWing.border(), pTemplate.border(), this.subsampling, imageWeight );

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

	public static void process( final File templateFile, final File dirPairs, final List< Pair< String, String > > pairs, final double imageWeight, final boolean showSummary, final boolean saveSummary ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		final ImagePlus templateImp = new ImagePlus( templateFile.getAbsolutePath() );

		final ImageStack stackGene = new ImageStack( templateImp.getWidth(), templateImp.getHeight() );
		final ImageStack stackBrightfield = new ImageStack( templateImp.getWidth(), templateImp.getHeight() );

		//final File wingFile = new File( "A12_002.tif" );

		for ( final Pair< String, String > pair : pairs )
		{
			final File wingFile = new File( dirPairs, pair.getA().substring( 0, pair.getA().indexOf( ".tif" ) ) );
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
	
			final Alignment alignment = new Alignment( template, wing, wingGene, imageWeight );
	
			final ImagePlus aligned = alignment.getAlignedImage();
			if ( aligned != null )
			{
				stackBrightfield.addSlice( wingFile.getName(), aligned.getStack().getProcessor( 2 ) );
				stackGene.addSlice( wingFile.getName(), aligned.getStack().getProcessor( 3 ) );
				new FileSaver( aligned ).saveAsZip( wingSavedFile.getAbsolutePath() );
	
				alignment.saveTransform( "transformed image '" + wingSavedFile.getAbsolutePath() + "'", wingSavedLog );
			}
			else
			{
				stackBrightfield.addSlice( wingFile.getName(), wing );
				stackGene.addSlice( wingFile.getName(), wingGene );
			}
		}

		if ( showSummary )
		{
			new ImagePlus( "all_gene", stackGene ).show();
			new ImagePlus( "all_brightfield", stackBrightfield ).show();
		}

		if ( saveSummary )
		{
			if ( stackGene.getSize() == 1 )
			{
				new FileSaver( new ImagePlus( "all_gene", stackGene ) ).saveAsTiff( new File( dirPairs, "all_gene.tif" ).getAbsolutePath() );
				new FileSaver( new ImagePlus( "all_brightfield", stackBrightfield ) ).saveAsTiff( new File( dirPairs, "all_brightfield.tif" ).getAbsolutePath() );
			}
			else
			{
				new FileSaver( new ImagePlus( "all_gene", stackGene ) ).saveAsTiffStack( new File( dirPairs, "all_gene.tif" ).getAbsolutePath() );
				new FileSaver( new ImagePlus( "all_brightfield", stackBrightfield ) ).saveAsTiffStack( new File( dirPairs, "all_brightfield.tif" ).getAbsolutePath() );
			}
		}
	}

	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		new ImageJ();

		final File dir = new File( "/Users/preibischs/Documents/Drosophila Wing Gompel/samples/B16" );

		final List< Pair< String, String > > pairs = CommonFileName.pairedImages( dir );

		for ( final Pair< String, String > pair : pairs )
			System.out.println( pair.getA() + " <> " + pair.getB() );

		final File templateFile = new File( "wing_template_A13_2014_01_31.tif" );

		process( templateFile, dir, pairs, 0.5, true, true );
	}
}
