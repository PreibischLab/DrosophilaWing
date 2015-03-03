package wt.tesselation;

import ij.gui.Roi;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import mpicbg.spim.io.TextFileAccess;
import net.imglib2.IterableRealInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import wt.tesselation.error.CircularityError;
import wt.tesselation.error.Error;
import wt.tesselation.error.QuadraticError;
import wt.tesselation.pointupdate.DistancePointUpdater;
import wt.tesselation.pointupdate.PointUpdater;

public class TesselationThread implements Runnable
{
	final private int targetArea;

	final private int[][] mask;
	final private int area;
	final private int numPoints, id;
	final private HashMap< Integer, RealPoint > locationMap;
	final private Search search;
	final private Random rnd;
	final private Error errorMetricArea;
	final private Error errorMetricCirc;
	final private double targetCircle;

	private double errorArea, errorCirc, error;
	private int iteration;
	private AtomicBoolean runNextIteration;
	private boolean stopThread;

	private double lastDX, lastDY, lastDist, lastSigma;
	private int lastDir, lastIteration;
	private boolean lastIterationUpdated;
	private boolean iterationFinished;

	private PrintWriter logFile;

	public TesselationThread( final int id, final Roi r, final Img< FloatType > img, final int targetArea )
	{
		this( id, r, img, targetArea, null );
	}

	public TesselationThread( final int id, final Roi r, final Img< FloatType > img, final int targetArea, final File currentState )
	{
		this.targetArea = targetArea;

		this.mask = Tesselation.makeMask( img, r );
		this.area = mask.length;
		this.numPoints = area / targetArea;
		this.locationMap = new HashMap< Integer, RealPoint >();
		if ( currentState == null )
			this.search = new Search( Tesselation.createRandomPoints( img, numPoints, r, locationMap ) );
		else
			this.search = new Search( Tesselation.loadPoints( currentState, img.numDimensions(), numPoints, locationMap ) );

		this.rnd = new Random( 1353 );
		this.errorMetricArea = new QuadraticError();
		this.errorMetricCirc = new CircularityError();
		this.id = id;

		if ( new File( "log_segment_" + id() + ".txt" ).exists() )
		{
			int updateId = 0;

			File file;

			do
			{
				++updateId;
				file = new File( "log_segment_" + id() + "_" + updateId + ".txt" );
			}
			while ( file.exists() );
			this.logFile = TextFileAccess.openFileWrite( file );
		}

		// initial compute areas
		Tesselation.update( mask, search );

		// initial compute simple statistics
		this.targetCircle = 0;
		this.errorArea = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		this.errorCirc = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		this.error = computeError( errorArea, errorCirc );
		this.iteration = 0;
		this.runNextIteration = new AtomicBoolean( false );
		this.stopThread = false;
	}

	protected double computeError( final double errorArea, final double errorCirc )
	{
		return errorArea + 300*errorCirc;
	}

	protected double normError( final double error )
	{
		return ( error / (double)numPoints() ) * 169.0; // error relative to the original dataset I tested on so the function works
	}

	protected double getFactor( final double error )
	{
		return -0.23948 + 13.88349*Math.exp(-(Math.log10( error )-5.24347)/0.05411) + 2.69144*Math.exp(-(Math.log10( error )-5.24347)/0.44634);
	}

	public int area() { return area; }
	public int numPoints() { return numPoints; }
	public int targetArea() { return targetArea; }
	public int id() { return id; }
	public double error() { return error; }
	public double errorCirc() { return errorCirc; }
	public double errorArea() { return errorArea; }
	public int iteration() { return iteration; }
	public PrintWriter logFile() { return logFile; }

	public IterableRealInterval< Segment > pointList() { return search.realInterval; }
	public Search search() { return search; }
	public int[][] mask() { return mask; }
	public HashMap< Integer, RealPoint > locationMap() { return locationMap; }

	public double lastDX() { return lastDX; }
	public double lastDY() { return lastDY; }
	public int lastDir() { return lastDir; }
	public double lastdDist() { return lastDist; }
	public double lastdSigma() { return lastSigma; }
	public int lastIteration() { return lastIteration; }
	public boolean lastIterationUpdated() { return lastIterationUpdated; }

	public void stopThread() { this.stopThread = true; }
	public synchronized void runNextIteration()
	{
		this.iterationFinished = false;
		this.runNextIteration.set( true );
	}
	public boolean iterationFinished() { return iterationFinished; }

	/*
		1	2020073.0	724.1660295533733	2237322.808866012	16	597	-40.0	0	5.0
		2	2016975.0	731.9594495755372	2236562.834872661	16	678	40.0	0	5.0
		3	1914239.0	731.8947097282255	2133807.4129184675	21	678	-40.0	0	10.0
		6	1856003.0	741.2279171116423	2078371.3751334928	21	638	80.0	0	5.0
		8	1856003.0	741.2279171116423	2041351.684549911	21	638	-80.0	0	0.0
		18	1856003.0	741.2279171116423	2012215.4622025955	21	638	40.0	0	0.0
		19	1811593.0	756.0759042217894	2038415.7712665368	28	638	40.0	0	10.0
	 */

	public void shake( final double amount, final Random rnd )
	{
		for ( final RealPoint p : locationMap.values() )
		{
			double rx = (rnd.nextDouble() - 0.5) * 2 * amount;
			double ry = (rnd.nextDouble() - 0.5) * 2 * amount;
			
			p.setPosition( p.getDoublePosition( 0 ) + rx, 0 );
			p.setPosition( p.getDoublePosition( 1 ) + ry, 1 );
		}

		Tesselation.update( mask, search );

		errorArea = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		errorCirc = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		error = computeError( errorArea, errorCirc );
	}

	/**
	 * @return - if was updated, otherwise false
	 */
	private boolean runIteration()
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
		next = Tesselation.neighborSegment( locationMap.get( next.id() ), search.kdTree, knearest, rnd );

		// backup all locations
		final ArrayList< RealPoint > backup = new ArrayList< RealPoint >();
		
		for ( final RealPoint p : locationMap.values() )
			backup.add( new RealPoint( p ) );
		
		// try to change the largest or the smallest
		final RealPoint p = locationMap.get( next.id() );

		double minError = error;
		double bestdx = 0;
		double bestdy = 0;
		int bestDir = -1;
		double bestDist = -1;
		
		double[] dist = new double[]{ -64, -32, -16, -8, -4, 4, 8, 16, 32, 64 };
		double[] sigmas = new double[]{ 40, 20, 10, 5, 0 };

		final double factor = getFactor( error );
		
		for ( int i = 0; i < dist.length; ++i )
			dist[ i ] /= Math.max( 0.1, factor );

		for ( int i = 0; i < sigmas.length; ++i )
			sigmas[ i ] /= Math.max( 1, factor*10.0 );

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

				updater.updatePoints( p, locationMap.values(), dx, dy );

				Tesselation.update( mask, search );

				final double errorA = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
				final double errorC = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
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
				for ( final RealPoint rp : locationMap.values() )
					rp.setPosition( backup.get( j++ ) );
			}

		// apply the best choice
		if ( bestDir >= 0 )
			updater.updatePoints( p, locationMap.values(), bestdx, bestdy );

		// update the image, area, etc.
		Tesselation.update( mask, search );

		errorArea = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		errorCirc = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		error = computeError( errorArea, errorCirc );

		if ( bestDir != -1 )
		{
			lastDX = bestdx;
			lastDY = bestdy;
			lastDir = bestDir;
			lastDist = bestDist;
			lastSigma = sigma;
			lastIteration = iteration;

			return true;
		}
		else
		{
			return false;
		}
	}

	@Override
	public void run()
	{
		do
		{
			if ( runNextIteration.getAndSet( false ) )
			{
				lastIterationUpdated = runIteration();
				iterationFinished = true;
			}

			try { Thread.sleep( 10 ); } catch (InterruptedException e) {}
		}
		while ( !stopThread );
	}
}
