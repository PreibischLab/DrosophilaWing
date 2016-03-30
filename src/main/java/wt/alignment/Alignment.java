package wt.alignment;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
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
		// find the initial alignment using SIFT feature matching (and if it is mirrored)
		this.transform1 = new InitialTransform( template, wing );

		// the object for the non-rigid alignment (for later down)
		this.nra = new NonrigidAlignment();

		// SIFT feature matching using SimilarityModel
		if ( !transform1.findInitialModel() )
			return;

		// do we need to mirror it? Whichever one provided better results in the SIFT matching
		if ( transform1.mirror() )
		{
			IJ.log( "Mirroring wing" );
			Mirror.mirror( wing, 0, Threads.numThreads() );
			Mirror.mirror( wingGene, 0, Threads.numThreads() );
		}

		// if the image was mirrored from the findInitialModel() method
		this.mirror = transform1.mirror();

		// the affine model from the findInitialModel() method
		this.model = transform1.model();

		// preprocess and transform in order to run the bunwarpj properly
		final Preprocess pTemplate = new Preprocess( template );
		
		// compute a big Gauss and subtract it
		pTemplate.homogenize();
		// TODO: Fourier filter
		// extend the image by 20%, use the simple method - and rembember the offset (how much we extended)
		this.offset = Util.getArrayFromValue( pTemplate.extend( 0.2f, true ), wing.numDimensions() );

		// preprocess and transform in order to run the bunwarpj properly
		final Preprocess pWing = new Preprocess( wing );
		
		// compute a big Gauss and subtract it
		pWing.homogenize();
		
		// extend the image by 20%, use the simple method
		pWing.extend( 0.2f, true );

		// transform pWing using the affine model from the initial transform
		final AffineModel2D model = new AffineModel2D();
		
		// for that, we need to update the transformation as we extended the images
		model.fit( transform1.createUpdatedMatches( this.offset ) );
		pWing.transform( model );

		//new ImagePlus( "template", ImageTools.wrap( pTemplate.output ) ).show();;
		//new ImagePlus( "wing", ImageTools.wrap( pWing.output ) ).show();;
		//SimpleMultiThreading.threadHaltUnClean();

		// compute non-rigid alignment (and before that adjust the image intensities for min, outofbounds, max and how they are mapped to 8 bit)
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
	
	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		new ImageJ();
		
		AlignmentProcess mainProcessing = new AlignmentProcess();
		mainProcessing.singleFileAlignmentTest();
		/*
		mainProcessing.setPairsDir(new File("/Users/spreibi/Documents/Drosophila Wing Gompel/samples/909"));
		mainProcessing.run();
		*/
	}
}

/**
 * 
 * @param templateFile - to register to
 * @param dirPairs - the image directory
 * @param pairs - the pairs (brightfield, gene) of images for each wing - or second one is null if first image contains both as stack
 * @param imageWeight - for bUnwarpJ
 * @param showSummary - show registered images
 * @param saveSummary - save file with registered images
 * @throws NotEnoughDataPointsException
 * @throws IllDefinedDataPointsException
 */
class AlignmentProcess
{
	protected File templateFile = new File("wing_template_A13_2014_01_31.tif");
	protected File dirRegistered  = null;
	protected File dirPairs      = null;
	protected List< Pair< String, String > > pairs = null;;
	protected double imageWeight  = 0.5;;
	protected boolean showSummary = true;
	protected boolean saveSummary = false;
	
	// setters
	AlignmentProcess setTemplate(   File    _templateFile )	{ templateFile = _templateFile; return this;}
	AlignmentProcess setImageWeight(double  _imageWeight  )	{ imageWeight  = _imageWeight;	return this;}
	AlignmentProcess setShowSummary(boolean _showSummary  )	{ showSummary  = _showSummary;	return this;}
	AlignmentProcess setSaveSummary(boolean _saveSummary  )	{ saveSummary  = _saveSummary;	return this;}
	
	AlignmentProcess setPairs(List< Pair< String, String > > _pairs ) {
		pairs    = _pairs;
		return this;
	}
	AlignmentProcess setPairsDir(File _dirPairs ) {
		dirPairs = _dirPairs;
		return this;
	}
	AlignmentProcess setPairs(File _dirPairs, List< Pair< String, String > > _pairs ) {
		setPairsDir(_dirPairs).setPairs(_pairs);
		return this;
	}
	
	AlignmentProcess addToPair(String _brightfieldName, String _fluorescenceName){
		if (pairs == null)
			pairs = new ArrayList< Pair< String, String > >();
		
		pairs.add( new ValuePair< String, String >( _brightfieldName, _fluorescenceName ) );
		return this;
	}
	AlignmentProcess addToPair(String _multiPage){
		return addToPair(_multiPage, null);
	}
	
	/**
	 * creates a directory for registered files ( current directory added with "registered"
	 * 
	 */
	void gessRegisteredDirectory()
	{
		if (dirPairs.equals(new File(""))){
			dirRegistered = new File("");
		} else {
			String lastPath = dirPairs.getAbsolutePath();
			lastPath = lastPath.substring(lastPath.lastIndexOf(File.separator)+1);
			
			dirRegistered = new File(dirPairs.getParentFile(), lastPath + "_registered" );
			dirRegistered.mkdir();
		}
	}
	
	public void singleFileAlignmentTest() throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// PROCESS ONE FILE (contains two slices brightfield & fluorescence) in the data directory "data/wings" (relative)
		setPairsDir(new File( "data/wings" ));
		setTemplate(new File( "data/wings/wing_template_A13_2014_01_31.tif" ));// what to register it to
		addToPair("909_dsRed_001.tif");
 		run();
	}
	
	void run() throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// check the input arguments
		if (templateFile == null) {return;}
		if (dirPairs     == null) {return;}
		if (pairs        == null) {
			setPairs(CommonFileName.pairedImages( dirPairs ));
			for ( final Pair< String, String > pair : pairs )
				System.out.println( pair.getA() + " <-> " + pair.getB() );
		}
		if (dirRegistered == null){
			gessRegisteredDirectory();
		}
		
		
		
		final ImagePlus templateImp = new ImagePlus( templateFile.getPath() );
		
		final ImageStack stackGene        = new ImageStack( templateImp.getWidth(), templateImp.getHeight() );
		final ImageStack stackBrightfield = new ImageStack( templateImp.getWidth(), templateImp.getHeight() );
		
		for ( final Pair< String, String > pair : pairs )
		{
			final File wingFile;
			
			if ( pair.getB() == null )
				wingFile = new File(dirPairs, pair.getA() ); // already two images in one file, just use the first file
			else
				wingFile = new File( dirPairs, pair.getA().substring( 0, pair.getA().indexOf( ".tif" ) ) ); // two different images
			final File wingSavedFile = new File( dirRegistered, wingFile.getName() + ".aligned.zip" );
			final File wingSavedLog  = new File( dirRegistered, wingFile.getName() + ".aligned.txt" );
			
			if ( wingSavedFile.exists() && wingSavedLog.exists() )
			{
				System.out.println( wingFile.getAbsolutePath() + " already processed, ignoring. If you want to reprocess, delete " + wingSavedFile.getAbsoluteFile() + " to override." );
				continue;
			}
			
			// this one figures out if it is two slices, or find the corresponding file 
			// it see which one is brighter and sort it accordingly
			final ImagePlus wingImp = ImageTools.loadImage( wingFile );
			if ( wingImp == null )
				throw new RuntimeException();
			
			final Img< FloatType > template = ImageTools.convert( templateImp, 0 );
			final Img< FloatType > wing     = ImageTools.convert( wingImp, 0 );
			final Img< FloatType > wingGene = ImageTools.convert( wingImp, 1 );
	
			
			// this is the main alignment work done here
			final Alignment alignment = new Alignment( template, wing, wingGene, imageWeight );
	
			// saves ?? to hard drive TODO
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
		
		if ( showSummary && stackGene.getSize() > 0 && stackBrightfield.getSize() > 0  )
		{
			new ImagePlus( "all_gene", stackGene ).show();
			new ImagePlus( "all_brightfield", stackBrightfield ).show();
		}
		
		if ( saveSummary && stackGene.getSize() > 0 && stackBrightfield.getSize() > 0 )
		{
			if ( stackGene.getSize() == 1 )
			{
				new FileSaver( new ImagePlus( "all_gene"       , stackGene        ) ).saveAsTiff( new File( dirRegistered, "all_gene.tif"        ).getAbsolutePath() );
				new FileSaver( new ImagePlus( "all_brightfield", stackBrightfield ) ).saveAsTiff( new File( dirRegistered, "all_brightfield.tif" ).getAbsolutePath() );
			}
			else
			{
				new FileSaver( new ImagePlus( "all_gene"       , stackGene        ) ).saveAsTiffStack( new File( dirRegistered, "all_gene.tif"        ).getAbsolutePath() );
				new FileSaver( new ImagePlus( "all_brightfield", stackBrightfield ) ).saveAsTiffStack( new File( dirRegistered, "all_brightfield.tif" ).getAbsolutePath() );
			}
		}
	}
}