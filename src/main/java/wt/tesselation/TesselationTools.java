package wt.tesselation;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.io.Opener;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import mpicbg.spim.io.TextFileAccess;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class TesselationTools
{
	public static void drawExpandShrink( final ImagePlus imp, final Iterable< RealPoint > now, final Iterable< RealPoint > before )
	{
		Overlay o = imp.getOverlay();
		
		if ( o == null )
		{
			o = new Overlay();
			imp.setOverlay( o );
		}

		final Iterator< RealPoint > a = now.iterator();
		final Iterator< RealPoint > b = before.iterator();

		while ( a.hasNext() )
		{
			final RealPoint to = a.next();
			final RealPoint from = b.next();

			final Line lr = new Line(
					Math.round( from.getFloatPosition( 0 ) ), Math.round( from.getFloatPosition( 1 ) ),
					Math.round( to.getFloatPosition( 0 ) ), Math.round( to.getFloatPosition( 1 ) ) );

			lr.setStrokeColor( Color.red );

			o.add( lr );
		}
	}

	public static void drawForces( final ImagePlus imp, final HashMap< Integer, Double > forces, final HashMap< Integer, RealPoint > locations )
	{
		Overlay o = imp.getOverlay();
		
		if ( o == null )
		{
			o = new Overlay();
			imp.setOverlay( o );
		}

		for ( final int id : locations.keySet() )
		{
			final double force = forces.get( id );
			final int absforce = Math.abs( (int)Math.round( force ) );
			final RealPoint p = locations.get( id );

			final OvalRoi or = new OvalRoi( Util.round( p.getFloatPosition( 0 ) - absforce/2 ), Util.round( p.getFloatPosition( 1 ) - absforce/2 ), absforce, absforce );

			if ( force >= 0 )
				or.setStrokeColor( Color.red );
			else
				or.setStrokeColor( Color.blue );

			o.add( or );
		}
	}

	public final static void drawRealPoint( final ImagePlus imp, final Collection< RealPoint > points )
	{
		Overlay o = imp.getOverlay();
		
		if ( o == null )
		{
			o = new Overlay();
			imp.setOverlay( o );
		}
		
		o.clear();
		
		for ( final RealPoint p : points )
		{
			final OvalRoi or = new OvalRoi( Util.round( p.getFloatPosition( 0 ) - 1 ), Util.round( p.getFloatPosition( 1 ) - 1 ), 3, 3 );
			or.setStrokeColor( Color.red );

			o.add( or );
		}
	}

	final public static void drawId( final int[][] mask, RandomAccessible< Segment > randomAccessible, final RandomAccessible< FloatType > img )
	{
		drawId( mask, randomAccessible, img, null );
	}

	final public static void drawId( final int[][] mask, RandomAccessible< Segment > randomAccessible, final RandomAccessible< FloatType > img, final Iterable< Segment > segmentMap )
	{
		float maxId = 1;

		if ( segmentMap != null )
		{
			for ( final Segment s : segmentMap )
				maxId = Math.max( s.id(), maxId );
		}

		final RandomAccess< Segment > ra = randomAccessible.randomAccess();
		final RandomAccess< FloatType > ri = img.randomAccess();
	
		for ( final int[] ml : mask )
		{
			ra.setPosition( ml );
			ri.setPosition( ml );

			ri.get().set( ra.get().id() / maxId );
		}
	}

	final public static void drawArea( final int[][] mask, RandomAccessible< Segment > randomAccessible, final RandomAccessible< FloatType > img )
	{
		final RandomAccess< Segment > ra = randomAccessible.randomAccess();
		final RandomAccess< FloatType > ri = img.randomAccess();
	
		for ( final int[] ml : mask )
		{
			ra.setPosition( ml );
			ri.setPosition( ml );

			ri.get().set( ra.get().area() );
		}
	}

	final public static void drawValue( final int[][] mask, RandomAccessible< Segment > randomAccessible, final RandomAccessible< FloatType > img )
	{
		final RandomAccess< Segment > ra = randomAccessible.randomAccess();
		final RandomAccess< FloatType > ri = img.randomAccess();
	
		for ( final int[] ml : mask )
		{
			ra.setPosition( ml );
			ri.setPosition( ml );

			ri.get().set( ra.get().value() );
		}
	}

	final public static Interval templateDimensions( final File roiDirectory )
	{
		if ( !roiDirectory.exists() || !roiDirectory.isDirectory() )
		{
			System.out.println( roiDirectory + " does not exist or is no directory." );
			return null;
		}

		final File f = new File( roiDirectory.getAbsolutePath(), "templatedims.txt" );

		if ( !f.exists() )
		{
			System.out.println( roiDirectory + " does not exist." );
			return null;
		}

		try
		{
			final BufferedReader in = TextFileAccess.openFileReadEx( f );

			final int x = Integer.parseInt( in.readLine().trim() );
			final int y = Integer.parseInt( in.readLine().trim() );

			System.out.println( "img dim: " + x + "x" + y );
			return new FinalInterval( x, y );
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.out.println( "Could not load dimensions from '" + f + "': " + e );
			return null;
		}
	}

	final public static int targetArea( final File roiDirectory )
	{
		if ( !roiDirectory.exists() || !roiDirectory.isDirectory() )
		{
			System.out.println( roiDirectory + " does not exist or is no directory." );
			return -1;
		}

		final File f = new File( roiDirectory.getAbsolutePath(), "targetarea.txt" );

		if ( !f.exists() )
		{
			System.out.println( roiDirectory + " does not exist." );
			return -1;
		}

		try
		{
			final BufferedReader in = TextFileAccess.openFileReadEx( f );

			final int area = Integer.parseInt( in.readLine().trim() );

			System.out.println( "targetarea: " + area );
			return area;
		}
		catch (IOException e)
		{
			e.printStackTrace();
			System.out.println( "Could not load target area from '" + f + "': " + e );
			return -1;
		}
	}

	final public static Segment maxInvCircularSegment( final Iterable< Segment > segmentMap )
	{
		Segment maxInvCircS = segmentMap.iterator().next();
		double maxInvCirc = maxInvCircS.invCircularity();
		
		for ( final Segment s : segmentMap )
		{
			double invCirc = s.invCircularity();
			
			if ( invCirc > maxInvCirc  )
			{
				maxInvCirc = invCirc;
				maxInvCircS = s;
			}
		}

		return maxInvCircS;
	}

	final public static Segment smallestSegment( final Iterable< Segment > segmentMap )
	{
		Segment min = segmentMap.iterator().next();

		for ( final Segment s : segmentMap )
			if ( s.area() < min.area() )
				min = s;

		return min;
	}

	/**
	 * Return the segment or one of the closest neighbors
	 * 
	 * @param location
	 * @param segmentTree
	 * @param k
	 * @param rnd
	 * @return
	 */
	final public static Segment neighborSegment( final RealPoint location, final KDTree< Segment > segmentTree, final int k, final Random rnd )
	{
		final KNearestNeighborSearchOnKDTree< Segment > s = new KNearestNeighborSearchOnKDTree< Segment >( segmentTree, k );
		s.search( location );
		return s.getSampler( rnd.nextInt( k ) ).get();
	}

	final public static Segment largestSegment( final Iterable< Segment > segmentMap )
	{
		Segment max = segmentMap.iterator().next();

		for ( final Segment s : segmentMap )
			if ( s.area() > max.area() )
				max = s;

		return max;
	}

	final public static Segment randomSegment( final Iterable< Segment > segmentMap, final Random rnd )
	{
		final ArrayList< Segment > l = new ArrayList<Segment>();
		
		for ( final Segment s : segmentMap )
			l.add( s );

		return l.get( rnd.nextInt( l.size() ) );
	}

	public final static int[][] makeMask( final Interval interval, final Roi r )
	{
		int numElements = 0;

		for ( int y = 0; y < interval.dimension( 1 ); ++y )
			for ( int x = 0; x < interval.dimension( 0 ); ++x )
				if ( r.contains( x, y ) )
					++numElements;

		final int[][] mask = new int[ numElements ][ 2 ];

		int i = 0;

		for ( int y = 0; y < interval.dimension( 1 ); ++y )
			for ( int x = 0; x < interval.dimension( 0 ); ++x )
				if ( r.contains( x, y ) )
				{
					mask[ i ][ 0 ] = x;
					mask[ i ][ 1 ] = y;
					++i;
				}

		return mask;
	}

	public final static RealPointSampleList< Segment > createRandomPoints( RealInterval interval, int numPoints, final Roi r, final HashMap< Integer, RealPoint > locations )
	{
		// the number of dimensions
		int numDimensions = interval.numDimensions();
		
		// a random number generator
		Random rnd = new Random( 35235 );//System.currentTimeMillis() );
		
		// a list of Samples with coordinates
		RealPointSampleList< Segment > elements = new RealPointSampleList< Segment >( numDimensions );
	
		for ( int i = 0; i < numPoints; ++i )
		{
			RealPoint point = new RealPoint( numDimensions );

			boolean set = false;
			
			do
			{
				for ( int d = 0; d < numDimensions; ++d )
					point.setPosition( (int)Math.round( rnd.nextDouble() * ( interval.realMax( d ) - interval.realMin( d ) ) + interval.realMin( d ) ), d );
	
				if (
						( r == null || r.contains( Math.round( point.getFloatPosition( 0 ) ), Math.round( point.getFloatPosition( 1 ) ) ) ) &&
						!alreadyThere( point, locations.values() ) )
				{
					// add a new element with a random intensity in the range 0...1
					final Segment s = new Segment( i );
					elements.add( point, s );

					if ( locations != null )
						locations.put( s.id(), point );
					set = true;
				}
			}
			while ( !set );
		}
		
		return elements;
	}

	protected static boolean alreadyThere( final RealPoint p, final Iterable< RealPoint > otherPoints )
	{
		final int n = p.numDimensions();

		for ( final RealPoint q : otherPoints )
		{
			boolean allsame = true;

			for ( int d = 0; d < n; ++d )
				if ( p.getDoublePosition( d ) != q.getDoublePosition( d ) )
					allsame = false;

			if ( allsame )
				return true;
		}

		return false;
	}

	public final static RealPointSampleList< Segment > loadPoints( final File pointFile, final int numDimensions, final int numPoints, final HashMap< Integer, RealPoint > locations )
	{
		// a list of Samples with coordinates
		RealPointSampleList< Segment > elements = new RealPointSampleList< Segment >( numDimensions );

		final BufferedReader in = TextFileAccess.openFileRead( pointFile );

		int count = 0;

		try
		{
			while ( in.ready() )
			{
				final String[] line = in.readLine().trim().split( "\t" );
				final int id = Integer.parseInt( line[ 0 ] );
	
				final double[] coordinate = new double[ numDimensions ];
	
				for ( int d = 0; d < numDimensions; ++d )
					coordinate[ d ] = Double.parseDouble( line[ d + 1 ] );
	
				final RealPoint point = new RealPoint( numDimensions );
	
				point.setPosition( coordinate );
	
				final Segment s = new Segment( id );
				elements.add( point, s );
				++count;
	
				if ( locations != null )
					locations.put( s.id(), point );
			}

		
			in.close();
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Failed to open file '" + pointFile.getAbsolutePath() + "':" + e );
		}

		if ( count != numPoints )
			throw new RuntimeException( "Wrong number of points, should be " + numPoints + ", but is " + count + " in file: " + pointFile );

		System.out.println( "Loaded " + numPoints + " points." );

		return elements;
	}

	public static void writePoints( final TesselationThread t )
	{
		final PrintWriter out = TextFileAccess.openFileWrite( "segment_" + t.id() + ".points.txt" );

		for ( final Segment s : t.search().realInterval )
		{
			final RealPoint p = t.locationMap().get( s.id() );
			out.println( s.id() + "\t" + p.getDoublePosition( 0 ) + "\t" + p.getDoublePosition( 1 ) );
		}
		
		out.close();
	}

	public static String currentState( final TesselationThread t )
	{
		return 
				t.iteration() + "\t" +
				t.errorArea() + "\t" +
				t.errorCirc() + "\t" +
				t.error() + "\t" + 
				smallestSegment( t.pointList() ).area() + "\t" +
				largestSegment( t.pointList() ).area() + "\t" +
				t.lastdDist() + "\t" +
				t.lastDir() + "\t" +
				t.lastdSigma();
	}

	public static void printCurrentState( final TesselationThread t )
	{
		String s = currentState( t );

		t.logFile().println( s );
		System.out.println( t.id() + "\t" + s );
	}

	public static List< Roi > loadROIs( final List< File > segmentFiles )
	{
		final ArrayList< Roi > segments = new ArrayList< Roi >();
		final Opener o = new Opener();

		for ( final File file : segmentFiles )
			segments.add( (Roi)o.openRoi( file.getAbsolutePath() ) );

		return segments;
	}

	public static List< File > assembleSegments( final File dir )
	{
		if ( !dir.exists() || !dir.isDirectory() )
			return null;

		final String[] files = dir.list();
		Arrays.sort( files );

		final ArrayList< File > segments = new ArrayList< File >();

		for ( final String file : files )
			if ( file.toLowerCase().endsWith( ".roi" ) )
				segments.add( new File( dir, file ) );

		return segments;
	}

	public static List< File > assemblePoints( final File dir )
	{
		if ( !dir.exists() || !dir.isDirectory() )
			return null;

		final String[] files = dir.list();
		Arrays.sort( files );

		final ArrayList< File > points = new ArrayList< File >();

		for ( final String file : files )
			if ( file.toLowerCase().endsWith( ".points.txt" ) )
				points.add( new File( dir, file ) );

		return points;
	}

	public static RealPointSampleList< IntType > createEquallySpacedPoints( RealInterval interval, int numPointsX, int numPointsY, final PolygonRoi r )
	{
		// the number of dimensions
		int numDimensions = interval.numDimensions();

		// a list of Samples with coordinates
		RealPointSampleList< IntType > elements = new RealPointSampleList< IntType >( numDimensions );

		double incX = ( interval.realMax( 0 ) - interval.realMin( 0 ) ) / ( numPointsX - 1 );
		double incY = ( interval.realMax( 1 ) - interval.realMin( 1 ) ) / ( numPointsY - 1 );

		int i = 0;

		for ( int y = 0; y < numPointsY; ++y )
			for ( int x = 0; x < numPointsX; ++x )
				{
					RealPoint point = new RealPoint( numDimensions );
					
					if ( y % 2 == 0 )
						point.setPosition( Math.round( incX * x + interval.realMin( 0 ) ), 0 );
					else
						point.setPosition( Math.round( incX * x + incX * 0.5 + interval.realMin( 0 ) ), 0 );
					
					point.setPosition( incY * y + interval.realMin( 1 ), 1 );

					if ( r == null || r.contains( Math.round( point.getFloatPosition( 0 ) ), Math.round( point.getFloatPosition( 1 ) ) ) )
					{
						// add a new element with a random intensity in the range 0...1
						elements.add( point, new IntType( ++i ) );
					}
				}

		return elements;
	}
}
