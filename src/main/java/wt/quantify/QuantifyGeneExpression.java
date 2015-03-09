package wt.quantify;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

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

public class QuantifyGeneExpression
{
	/**
	 * The tesselation is constant for all registered images to be analyzed
	 */
	final private LoadTesselation tesselation;
	final private Interval interval;
	final LocalMaxima< FloatType > maxFinder;
	final ArrayList< Roi > rois;

	public QuantifyGeneExpression( final File roiDirectory )
	{
		this.tesselation = new LoadTesselation( roiDirectory );
		this.interval = this.tesselation.interval();
		this.rois = new ArrayList< Roi >();
		this.maxFinder = new SimpleLocalMaxima();
	}

	public void measure( final File alignedImage )
	{
		final Img< FloatType > gene = ImageTools.convert( new ImagePlus( alignedImage.getAbsolutePath() ), 1 );

		final Collection< RealPointValue< FloatType > > maxima = maxFinder.maxima( gene, new FloatType( 10 ) );

		final Img< FloatType > avgImg = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
		final ImagePlus avgImp = new ImagePlus( "voronoiId", ImageTools.wrap( avgImg ) );

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

			TesselationTools.drawValue( t.mask(), t.search().randomAccessible(), avgImg );
		}

		avgImp.resetDisplayRange();
		avgImp.show();

		final ImagePlus imp = new ImagePlus( "maxima", ImageTools.wrap( gene ) );
		imp.resetDisplayRange();
		//TesselationTools.drawRealPoint( imp, maxima );
		imp.show();
		
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

	public static void main( String[] args )
	{
		new ImageJ();

		final QuantifyGeneExpression qge = new QuantifyGeneExpression( new File( "SegmentedWingTemplate" ) );

		final ImagePlus impId = qge.tesselation().impId( true );
		TesselationTools.drawRealPoint( impId, qge.centerOfMasses() );
		impId.updateAndDraw();
		impId.show();
		
		qge.measure( new File( "A12_002.aligned.zip" ));
		
		
	}
}
