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
import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.real.FloatType;
import wt.tesselation.error.CircularityError;
import wt.tesselation.error.Error;
import wt.tesselation.error.QuadraticError;
import wt.tesselation.pointupdate.DistancePointUpdater;
import wt.tesselation.pointupdate.PointUpdater;
import wt.tesselation.pointupdate.SimplePointUpdater;

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
	private boolean stopThread, runExpandShrink;

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
		update( mask, search );

		// initial compute simple statistics
		this.targetCircle = 0;
		this.errorArea = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		this.errorCirc = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		this.error = computeError( errorArea, errorCirc );
		this.iteration = 0;
		this.runNextIteration = new AtomicBoolean( false );
		this.stopThread = false;
		this.runExpandShrink = false;
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
	public void setRunExpandShrink( final boolean state ) { this.runExpandShrink = state; }

	public void shake( final double amount )
	{
		for ( final RealPoint p : locationMap.values() )
		{
			double rx = (rnd.nextDouble() - 0.5) * 2 * amount;
			double ry = (rnd.nextDouble() - 0.5) * 2 * amount;
			
			p.setPosition( p.getDoublePosition( 0 ) + rx, 0 );
			p.setPosition( p.getDoublePosition( 1 ) + ry, 1 );
		}

		update( mask, search );

		errorArea = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		errorCirc = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		error = computeError( errorArea, errorCirc );
	}

	public boolean runExpandShrinkIteration( final int neighbors, final double scaleFactor )
	{
		++iteration;

		// for every segment compute the average error of the nearest n segments
		final KNearestNeighborSearchOnKDTree< Segment > sr = new KNearestNeighborSearchOnKDTree<Segment>( search.kdTree, neighbors );

		final HashMap< Integer, Double > expandShrink = new HashMap< Integer, Double >();
		final ArrayList< Segment > allSegments = new ArrayList< Segment >();

		Segment smallest = null;
		Segment largest = null;
		double eMin = Double.MAX_VALUE;
		double eMax = -Double.MAX_VALUE;

		for ( final Segment s : search.realInterval )
		{
			allSegments.add( s );

			final RealPoint p = locationMap.get( s.id() );
			sr.search( p );

			// if the error is positive means it has to shrink (area in average to big)
			// otherwise has to grow (area in average to small)
			error = 0;

			for ( int i = 0; i < neighbors; ++i )
			{
				final double area = sr.getSampler( i ).get().area();
				error += area - targetArea();
			}

			if ( error < eMin )
			{
				eMin = error;
				smallest = s;
			}

			if ( error > eMax )
			{
				eMax = error;
				largest = s;
			}

			expandShrink.put( s.id(), error );
		}

		// now expand/shrink from smallest to largest

		final Segment s1 = smallest;
		final Segment s2 = largest;

		final RealPoint p1 = locationMap.get( s1.id() );
		final RealPoint p2 = locationMap.get( s2.id() );

		final double dist = DistancePointUpdater.dist( p1, p2 );
		final double vx = ( p2.getDoublePosition( 0 ) - p1.getDoublePosition( 0 ) ) / dist;
		final double vy = ( p2.getDoublePosition( 1 ) - p1.getDoublePosition( 1 ) ) / dist;

		new DistancePointUpdater( 100 ).updatePoints( p1, locationMap.values(), vx * scaleFactor, vy * scaleFactor );

		// update the image, area, etc.
		update( mask, search );

		errorArea = normError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		errorCirc = normError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		error = computeError( errorArea, errorCirc );

		return true;
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
		final PointUpdater updater;
		
		if ( sigma == 0.0 )
			updater = new SimplePointUpdater();
		else
			updater = new DistancePointUpdater( sigma );

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

				update( mask, search );

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
		update( mask, search );

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

	final protected static void update( final int[][] mask, final Search search )
	{
		// update the new coordinates for the pointlist
		search.update();

		final RandomAccess< Segment > ra = search.randomAccessible.randomAccess();

		for ( final Segment s : search.realInterval )
		{
			s.setArea( 0 );
			s.pixels().clear();
		}

		for ( final int[] ml : mask )
		{
			ra.setPosition( ml );

			final Segment s = ra.get();
			s.incArea();
			s.pixels().add( ml );
		}
	}

	@Override
	public void run()
	{
		do
		{
			if ( runNextIteration.getAndSet( false ) )
			{
				if ( runExpandShrink )
					lastIterationUpdated = runExpandShrinkIteration( numPoints()/5, 10 );
				else
					lastIterationUpdated = runIteration();

				iterationFinished = true;
			}

			try { Thread.sleep( 10 ); } catch (InterruptedException e) {}
		}
		while ( !stopThread );
	}
}
