package wt.quantify;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.FileSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.neighborsearch.KNearestNeighborSearch;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import wt.alignment.ImageTools;
import wt.quantify.localmaxima.LocalMaxima;
import wt.quantify.localmaxima.RealPointValue;
import wt.quantify.localmaxima.SimpleLocalMaxima;
import wt.tesselation.LoadTesselation;
import wt.tesselation.Search;
import wt.tesselation.Segment;
import wt.tesselation.TesselationThread;
import wt.tesselation.TesselationTools;
import wt.tesselation.pointupdate.DistancePointUpdater;
import wt.tools.CommonFileName;

public class QuantifyGeneExpression
{
	/**
	 * The tesselation is constant for all registered images to be analyzed
	 */
	final private LoadTesselation tesselation;
	final private Interval interval;
	final LocalMaxima< FloatType > maxFinder;
	final ArrayList< Roi > rois;

	Img< FloatType > gene, measurement;

	public QuantifyGeneExpression( final File roiDirectory )
	{
		this.tesselation = new LoadTesselation( roiDirectory );
		this.interval = this.tesselation.interval();
		this.rois = new ArrayList< Roi >();
		this.maxFinder = new SimpleLocalMaxima();
	}

	public Interval interval() { return interval; }
	public Img< FloatType > lastGene() { return gene; }
	public ImagePlus lastGeneImp() { return new ImagePlus( "maxima", ImageTools.wrap( gene ) ); }
	public Img< FloatType > lastMeasurement() { return measurement; }
	public ImagePlus lastMeasurementImp() { return new ImagePlus( "measurement", ImageTools.wrap( measurement ) ); }

	public void measure( final File alignedImage )
	{
		final ImagePlus imp = new ImagePlus( alignedImage.getAbsolutePath() );
		this.gene = ImageTools.convert( imp, imp.getStackSize() - 1 );

		final Collection< RealPointValue< FloatType > > maxima = maxFinder.maxima( this.gene, new FloatType( 3 ) );

		this.measurement = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
		//final ImagePlus avgImp = new ImagePlus( "voronoiId", ImageTools.wrap( avgImg ) );

		// reset all values
		for ( final TesselationThread t : tesselation.tesselations() )
		{
			for ( final Segment s : t.search().segments() )
				s.resetPeaks();

			// for determining the corresponding Segment
			final RealRandomAccess< Segment > rra = Views.interpolate( t.search().randomAccessible(), new NearestNeighborInterpolatorFactory< Segment >() ).realRandomAccess();

			for ( final RealPointValue< FloatType > max : maxima )
				if ( t.roi().contains( Math.round( max.getFloatPosition( 0 ) ), Math.round( max.getFloatPosition( 1 ) ) ) )
				{
					rra.setPosition( max );
					rra.get().addPeak( max.get().get() );
				}
	
			for ( final Segment s : t.search().segments() )
			{
				s.setValue( s.sumPeakIntensity() / s.numPeaks() );
				//s.setValue( s.numPeaks() );
			}

			smoothValues( t.search(), t.locationMap(), 5 );

			TesselationTools.drawValue( t.mask(), t.search().randomAccessible(), this.measurement );
		}
	}

	public static void smoothValues( final Search< Segment > search, final HashMap< Integer, RealPoint > locationMap, final int numNeighbors )
	{
		for ( final Segment s : search.segments() )
			s.tmp = s.value();

		final KNearestNeighborSearch< Segment > kns = new KNearestNeighborSearchOnKDTree<Segment>( search.kdTree(), numNeighbors + 1 );

		for ( final Segment s : search.segments() )
		{
			final RealPoint sp = locationMap.get( s.id() );
			kns.search( sp );

			double weight = 0;
			double intensity = 0;

			for ( int i = 0; i < numNeighbors + 1; ++i )
			{
				final Segment t = kns.getSampler( i ).get();
				final RealPoint tp = locationMap.get( t.id() );

				final double dist = Math.pow( DistancePointUpdater.dist( sp, tp ), 2 );

				weight += dist;
				intensity += dist * t.tmp;
			}

			s.setValue( intensity / weight );
		}
	}

	public LoadTesselation tesselation() { return tesselation; }

	public Collection< RealPoint > centerOfMasses()
	{
		final ArrayList< RealPoint > list = new ArrayList< RealPoint >();

		for ( final TesselationThread t : tesselation.tesselations() )
			for ( final Segment s : t.search().segments() )
			list.add( new RealPoint( s.centerOfMass() ) );

		return list;
	}

	public static void process( final File tesselationDir, final File imageDir, final List< String > alignedImages, final boolean showTesselation, final boolean showResult, final boolean saveResult )
	{
		if ( !tesselationDir.exists() )
			throw new RuntimeException( "Tesselation directory '" + tesselationDir.getAbsolutePath() + "' does not exist." );

		if ( !tesselationDir.isDirectory() )
			throw new RuntimeException( "Tesselation directory '" + tesselationDir.getAbsolutePath() + "' is not a directory." );

		if ( !imageDir.exists() )
			throw new RuntimeException( "Image directory '" + imageDir.getAbsolutePath() + "' does not exist." );

		if ( !imageDir.isDirectory() )
			throw new RuntimeException( "Image directory '" + imageDir.getAbsolutePath() + "' is not a directory." );

		final QuantifyGeneExpression qge = new QuantifyGeneExpression( tesselationDir );

		if ( showTesselation )
		{
			final ImagePlus impId = qge.tesselation().impId( true );
			TesselationTools.drawRealPoint( impId, qge.centerOfMasses() );
			impId.updateAndDraw();
			impId.show();
		}

		final ImageStack stack = new ImageStack( (int)qge.interval().dimension( 0 ), (int)qge.interval().dimension( 1 ) );

		for ( final String wingFileName : alignedImages )
		{
			final File wingFile = new File( imageDir, wingFileName );
	
			IJ.log( "Quantifying: " + wingFile.getAbsolutePath() );

			if ( !wingFile.exists() )
			{
				IJ.log( "ERROR, could not load file '" + wingFile.getAbsolutePath() + "'" );
				continue;
			}

			qge.measure( wingFile );
			
			stack.addSlice( wingFile.getName(), qge.lastMeasurementImp().getProcessor() );
			//qge.lastMeasurementImp().show();
		}

		if ( showResult )
		{
			new ImagePlus( "quantification", stack ).show();
		}

		if ( saveResult )
		{
			if ( stack.getSize() == 1 )
				new FileSaver( new ImagePlus( "quantification", stack ) ).saveAsTiff( new File( imageDir, "all_quantified.tif" ).getAbsolutePath() );
			else
				new FileSaver( new ImagePlus( "quantification", stack ) ).saveAsTiffStack( new File( imageDir, "all_quantified.tif" ).getAbsolutePath() );
		}
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final File imageDir = new File( "/Users/preibischs/Documents/Drosophila Wing Gompel/samples/B16" );
		final File tesselationDir = new File( "/Users/preibischs/Documents/Drosophila Wing Gompel/SegmentedWingTemplate" );

		final List< String > alignedImages = CommonFileName.getAlignedImages( imageDir );

		//final ArrayList< String > files = new ArrayList< String >();
		//files.add( new File( "wing_B16_dsRed_001.aligned.zip" ) );
		//final File imageDir = new File( "/Users/preibischs/Documents/Drosophila Wing Gompel/samples/B16" );

		process( tesselationDir, imageDir, alignedImages, false, true, false );
	}
}
