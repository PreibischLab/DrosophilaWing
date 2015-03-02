package wt.tesselation;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.io.FileSaver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import wt.tesselation.error.CircularityError;
import wt.tesselation.error.Error;
import wt.tesselation.error.QuadraticError;
import wt.tesselation.pointupdate.DistancePointUpdater;
import wt.tesselation.pointupdate.PointUpdater;

public class TesselationThread
{
	final PolygonRoi r;
	final Img< FloatType > img;
	final ImagePlus imp;
	final int targetArea;

	final int[][] mask;
	final int area;
	final int numPoints;
	final HashMap< Integer, RealPoint > locations;
	final Search search;
	final Random rnd;
	final Error errorMetricArea;
	final Error errorMetricCirc;
	final double targetCircle;

	double errorArea, errorCirc;
	int iteration;

	public TesselationThread( final PolygonRoi r, final Img< FloatType > img, final ImagePlus imp, final int targetArea )
	{
		this.r = r;
		this.img = img;
		this.imp = imp;
		this.targetArea = targetArea;

		this.mask = Tesselation.makeMask( img, r );
		this.area = mask.length;
		this.numPoints = area / targetArea;
		this.locations = new HashMap< Integer, RealPoint >();
		this.search = new Search( Tesselation.createRandomPoints( img, numPoints, r, locations ) );
		this.rnd = new Random( 1353 );
		this.errorMetricArea = new QuadraticError();
		this.errorMetricCirc = new CircularityError();

		// initial compute areas
		Tesselation.update( mask, search );

		// initial compute simple statistics
		this.targetCircle = 0;
		this.errorArea = errorMetricArea.computeError( search.realInterval, targetArea );
		this.errorCirc = errorMetricCirc.computeError( search.realInterval, targetCircle );

		this.iteration = 0;
	}

	/*
		1	2020073.0	724.1660295533733	2237322.808866012	16	597	-40.0	0	5.0
		2	2016975.0	731.9594495755372	2236562.834872661	16	678	40.0	0	5.0
		3	1914239.0	731.8947097282255	2133807.4129184675	21	678	-40.0	0	10.0
		6	1856003.0	741.2279171116423	2078371.3751334928	21	638	80.0	0	5.0
		8	1856003.0	741.2279171116423	2041351.684549911	21	638	-80.0	0	0.0
		18	1856003.0	741.2279171116423	2012215.4622025955	21	638	40.0	0	0.0
		19	1811593.0	756.0759042217894	2038415.7712665368	28	638	40.0	0	10.0
	 */

	/**
	 * @return - if was updated, otherwise false
	 */
	public boolean runIteration()
	{
		++iteration;

		// select the next segment to try to change
		Segment next;
		int knearest = 4;

		if ( iteration % 5 == 0 )
			next = Tesselation.randomSegment( search.realInterval, rnd );
		else if ( iteration % 5 == 1 )
			next = Tesselation.smallestSegment( search.realInterval );
		else if ( iteration % 5 == 2 )
			next = Tesselation.largestSegment( search.realInterval );
		else if ( iteration % 5 == 3 )
			next = Tesselation.randomSegment( search.realInterval, rnd );
		else
			next = Tesselation.maxInvCircularSegment( search.realInterval );

		// select a close neighbor to the smallest, largest or random segment
		next = Tesselation.neighborSegment( locations.get( next.id() ), search.kdTree, knearest, rnd );

		// backup all locations
		final ArrayList< RealPoint > backup = new ArrayList< RealPoint >();
		
		for ( final RealPoint p : locations.values() )
			backup.add( new RealPoint( p ) );
		
		// try to change the largest or the smallest
		final RealPoint p = locations.get( next.id() );

		double minError = errorArea + 300*errorCirc;
		double bestdx = 0;
		double bestdy = 0;
		int bestDir = -1;
		double bestDist = -1;
		
		double[] dist = new double[]{ -64, -32, -16, -8, -4, 4, 8, 16, 32, 64 };
		double[] sigmas = new double[]{ 40, 20, 10, 5, 0 };

		for ( int i = 0; i < dist.length; ++i )
			dist[ i ] /= Math.max( 0.1, ( iteration / (5*100.0) ) );

		for ( int i = 0; i < sigmas.length; ++i )
			sigmas[ i ] /= Math.max( 1, ( iteration / (5*1000.0) ) );

		// randomly select one of the sigmas
		final double sigma = sigmas[ rnd.nextInt( sigmas.length ) ];
		final PointUpdater updater = new DistancePointUpdater( sigma );

		for ( int i = 0; i < dist.length; ++i )
			for ( int dir = 0; dir <= 1; ++dir )
			{
				double dx = 0;
				double dy = 0;

				if ( dir == 0 )
					dx = dist[ i ];
				else
					dy = dist[ i ];

				updater.updatePoints( p, locations.values(), dx, dy );

				Tesselation.update( mask, search );

				final double errorA = errorMetricArea.computeError( search.realInterval, targetArea );
				final double errorC = errorMetricCirc.computeError( search.realInterval, targetCircle );
				final double errorTest = errorA + 300*errorC;

				if ( errorTest < minError )
				{
					minError = errorTest;
					bestdx = dx;
					bestdy = dy;
					bestDir = dir;
					bestDist = dist[ i ];
				}
				
				// restore positions
				int j = 0;
				for ( final RealPoint rp : locations.values() )
					rp.setPosition( backup.get( j++ ) );
			}

		// apply the best choice
		if ( bestDir >= 0 )
			updater.updatePoints( p, locations.values(), bestdx, bestdy );

		// update the image, area, etc.
		Tesselation.update( mask, search );

		errorArea = errorMetricArea.computeError( search.realInterval, targetArea );
		errorCirc = errorMetricCirc.computeError( search.realInterval, targetCircle );

		if ( bestDir != -1 )
		{
			System.out.println( iteration + "\t" + errorArea + "\t" + errorCirc + "\t" + minError + "\t" + Tesselation.smallestSegment( search.realInterval ).area() + "\t" + Tesselation.largestSegment( search.realInterval ).area() + "\t" + bestDist + "\t" + bestDir + "\t" + sigma );
		
			// update the drawing
			Tesselation.drawArea( mask, search.randomAccessible, img );
			Tesselation.drawOverlay( imp, locations.values() );

			imp.updateAndDraw();
			new FileSaver( imp ).saveAsZip( "movie/voronoi_" + iteration + ".zip" );
			return true;
		}
		else
		{
			return false;
		}
	}
}
