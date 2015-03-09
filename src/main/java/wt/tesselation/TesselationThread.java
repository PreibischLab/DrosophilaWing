package wt.tesselation;

import ij.ImagePlus;
import ij.gui.Roi;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import mpicbg.spim.io.TextFileAccess;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import wt.tesselation.error.CircularityError;
import wt.tesselation.error.Error;
import wt.tesselation.error.QuadraticError;
import wt.tesselation.pointupdate.DistancePointUpdater;
import wt.tesselation.pointupdate.PointUpdater;
import wt.tesselation.pointupdate.SimplePointUpdater;

public class TesselationThread implements Runnable
{
	final private int targetArea;
	final private double targetCircle;

	final Roi r;
	final private int[][] mask;
	final private int area;
	final private int numPoints, id;
	final private HashMap< Integer, RealPoint > locationMap;
	final private Search< Segment > search;
	final private Random rnd;
	final private Error errorMetricArea;
	final private Error errorMetricCirc;

	private double errorArea, errorCirc, error;
	private int iteration;
	private AtomicBoolean runNextIteration;
	private boolean stopThread;

	private double lastDX, lastDY, lastDist, lastSigma;
	private int lastDir, lastIteration;
	private boolean lastIterationUpdated;
	private boolean iterationFinished;

	private PrintWriter logFile;

	public TesselationThread( final int id, final Roi r, final Interval interval, final int targetArea )
	{
		this( id, r, interval, targetArea, null );
	}

	public TesselationThread( final int id, final Roi r, final Interval interval, final int targetArea, final File currentState )
	{
		this.targetArea = targetArea;

		this.r = r;
		this.mask = TesselationTools.makeMask( interval, r );
		this.area = mask.length;
		this.numPoints = area / targetArea;
		this.locationMap = new HashMap< Integer, RealPoint >();
		if ( currentState == null )
			this.search = new Search< Segment >( TesselationTools.createRandomPoints( interval, numPoints, r, locationMap ) );
		else
			this.search = new Search< Segment >( TesselationTools.loadPoints( currentState, interval.numDimensions(), numPoints, locationMap ) );

		this.rnd = new Random( 1353 );
		this.errorMetricArea = new QuadraticError();
		this.errorMetricCirc = new CircularityError();
		this.id = id;
		this.logFile = null; // just open it once it is actually requested

		// initial compute areas
		update( mask, search );

		// initial compute simple statistics
		this.targetCircle = 0;
		this.errorArea = normLocalError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		this.errorCirc = normLocalError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		this.error = computeLocalError( errorArea, errorCirc );
		this.iteration = 0;
		this.runNextIteration = new AtomicBoolean( false );
		this.stopThread = false;
	}

	public Roi roi() { return r; }
	public int area() { return area; }
	public int numPoints() { return numPoints; }
	public int targetArea() { return targetArea; }
	public double targetCircle() { return targetCircle; }
	public int id() { return id; }
	public double error() { return error; }
	public double errorCirc() { return errorCirc; }
	public double errorArea() { return errorArea; }
	public int iteration() { return iteration; }
	public PrintWriter logFile()
	{
		if ( this.logFile == null )
		{
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
			else
			{
				this.logFile = TextFileAccess.openFileWrite( new File( "log_segment_" + id() + ".txt" ) );
			}
		}

		return logFile;
	}

	public IterableRealInterval< Segment > pointList() { return search.realInterval; }
	public Search< Segment > search() { return search; }
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

	protected double computeLocalError( final double errorArea, final double errorCirc )
	{
		return errorArea + 300*errorCirc;
	}

	protected double normLocalError( final double error )
	{
		return ( error / (double)numPoints() ) * 169.0; // error relative to the original dataset I tested on so the function works
	}

	protected double getLocalErrorFactor( final double error )
	{
		return -0.23948 + 13.88349*Math.exp(-(Math.log10( error )-5.24347)/0.05411) + 2.69144*Math.exp(-(Math.log10( error )-5.24347)/0.44634);
	}

	public double computeGlobalError( final int neighbors, final boolean setValue, final HashMap< Integer, Double > forces )
	{
		// for every segment compute the average error of the nearest n segments
		final KNearestNeighborSearchOnKDTree< Segment > sr = new KNearestNeighborSearchOnKDTree< Segment >( search.kdTree, neighbors );

		double sumForces = 0;

		for ( final Segment s : search.realInterval )
		{
			final RealPoint p = locationMap.get( s.id() );
			sr.search( p );

			// if the error is positive means it has to push points away (area in average to small)
			// otherwise has to pull points (area in average to big)
			double error = 0;

			for ( int i = 0; i < neighbors; ++i )
			{
				final double area = sr.getSampler( i ).get().area();
				error += area - targetArea();
			}

			error /= (double)neighbors;
			final double force = -error;

			sumForces += Math.abs( force );

			if ( forces != null )
				forces.put( s.id(), force );

			if ( setValue )
				s.setValue( (float)error );
		}

		return sumForces / (double)search.realInterval.size();
	}

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

		errorArea = normLocalError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		errorCirc = normLocalError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		error = computeLocalError( errorArea, errorCirc );
	}

	public double expandShrink( final int neighbors, final ImagePlus imp )
	{
		// backup all locations as we update the positions on the way
		final HashMap< Integer, RealPoint > backup = new HashMap< Integer, RealPoint >();

		for ( final int i : locationMap.keySet() )
			backup.put( i, new RealPoint( locationMap.get( i ) ) );

		final HashMap< Integer, Double > forces = new HashMap< Integer, Double >();
		computeGlobalError( neighbors, true, forces );

		//System.out.println( id() + "\t" + error );

		//if ( imp != null )
		//	TesselationTools.drawForces( imp, forces, locationMap );

		//
		// compute the new locations
		//

		final double sigma = 10000;
		final double two_sq_sigma = 2 * sigma * sigma;

		for ( final Segment s : search.realInterval )
		{
			final RealPoint ls = backup.get( s.id() );

			final double xs = ls.getDoublePosition( 0 );
			final double ys = ls.getDoublePosition( 1 );

			// sum up how much other segments pull/push segment s
			double svx = 0;
			double svy = 0;
			double sw = 0;

			for ( final Segment p : search.realInterval )
			{
				if ( p.id() == s.id() )
					continue;

				final RealPoint lp = backup.get( p.id() );
				final double force = forces.get( p.id() );//Math.sqrt( Math.abs( forces.get( p.id() ) ) ) * Math.signum( forces.get( p.id() ) );
				final double dist = DistancePointUpdater.dist( ls, lp );
				final double vx = ( xs - lp.getDoublePosition( 0 ) ) / dist;
				final double vy = ( ys - lp.getDoublePosition( 1 ) ) / dist;

				final double w = Math.exp( -( dist * dist ) / two_sq_sigma );
	
				svx += force * vx * w;
				svy += force * vy * w;
				sw += w;
			}

			final double vx = svx/sw;
			final double vy = svy/sw;

			final double xs_new = xs + vx;
			final double ys_new = ys + vy;

			// do not push outside of the ROI
			if ( r.contains( (int)Math.round( xs_new ), (int)Math.round( ys_new ) ))
				locationMap.get( s.id() ).setPosition( new double[]{ xs_new, ys_new } );
		}

		// update the image, area, etc.
		update( mask, search );

		this.errorArea = normLocalError( errorMetricArea.computeError( search.realInterval, targetArea ) );
		this.errorCirc = normLocalError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
		this.error = computeLocalError( errorArea, errorCirc );

		error = computeGlobalError( neighbors, true, forces );

		if ( imp != null )
			TesselationTools.drawExpandShrink( imp, locationMap.values(), backup.values() );

		return sigma;
	}

	/**
	 * @return - if was updated, otherwise false
	 */
	private boolean runIteration()
	{
		++iteration;
		/*
		if ( iteration % 200 == rnd.nextInt( 200 ) )
		{
			lastSigma = expandShrink( numPoints()/15, null );

			lastDX = 0;
			lastDY = 0;
			lastDir = 0xf;
			lastDist = 0;
			lastIteration = iteration;

			return true; // updated
		}
		else*/
		{
			// select the next segment to try to change
			Segment next;
			int knearest = 4;
	
			if ( iteration % 5 == 0 )
				next = TesselationTools.randomSegment( search.realInterval, rnd );
			else if ( iteration % 5 == 1 )
				next = TesselationTools.smallestSegment( search.realInterval );
			else if ( iteration % 5 == 2 )
				next = TesselationTools.largestSegment( search.realInterval );
			else if ( iteration % 5 == 3 )
				next = TesselationTools.randomSegment( search.realInterval, rnd );
			else
				next = TesselationTools.maxInvCircularSegment( search.realInterval );
	
			// select a close neighbor to the smallest, largest or random segment
			next = TesselationTools.neighborSegment( locationMap.get( next.id() ), search.kdTree, knearest, rnd );
	
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
	
			final double factor = getLocalErrorFactor( error );
			
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
	
					final double errorA = normLocalError( errorMetricArea.computeError( search.realInterval, targetArea ) );
					final double errorC = normLocalError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
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
	
			errorArea = normLocalError( errorMetricArea.computeError( search.realInterval, targetArea ) );
			errorCirc = normLocalError( errorMetricCirc.computeError( search.realInterval, targetCircle ) );
			error = computeLocalError( errorArea, errorCirc );
	
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
	}

	final protected static void update( final int[][] mask, final Search< Segment > search )
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
				lastIterationUpdated = runIteration();
				iterationFinished = true;
			}

			try { Thread.sleep( 10 ); } catch (InterruptedException e) {}
		}
		while ( !stopThread );
	}
}
