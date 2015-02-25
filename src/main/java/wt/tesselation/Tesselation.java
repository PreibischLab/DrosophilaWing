package wt.tesselation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.io.Opener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import wt.Alignment;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import net.imglib2.Interval;
import net.imglib2.IterableRealInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPointSampleList;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class Tesselation
{
	public Tesselation( final Img< FloatType > wing, final Img< FloatType > gene, final List< PolygonRoi > segments )
	{
		/*
		ImagePlus wingImp = new ImagePlus( "wing", Alignment.wrap( wing ) );
		ImagePlus geneImp = new ImagePlus( "gene", Alignment.wrap( gene ) );

		geneImp.show();
		//geneImp.setRoi( segments.get( 0 ) );

		
		Overlay o = new Overlay();
		
		for ( final PolygonRoi r : segments )
			o.add( r );
		geneImp.setOverlay( o );
		*/
		
		final int targetArea = 200;

		renderVoronoi( segments.get( 3 ), gene.factory().create( gene, gene.firstElement() ), targetArea );
	}

	protected static class Search
	{
		// point list
		final IterableRealInterval< Segment > realInterval;

		// the factory
		final NearestNeighborSearchInterpolatorFactory< Segment > factory = new NearestNeighborSearchInterpolatorFactory< Segment >();

		// for searching
		KDTree< Segment > kdTree;

		// using nearest neighbor search we will be able to return a value an any position in space
		NearestNeighborSearch< Segment > search;

		// make it into RealRandomAccessible using nearest neighbor search
		RealRandomAccessible< Segment > realRandomAccessible;

		// convert it into a RandomAccessible which can be displayed
		RandomAccessible< Segment > randomAccessible;

		public Search( IterableRealInterval< Segment > realInterval )
		{
			this.realInterval = realInterval;

			update();
		}

		public void update()
		{
			this.kdTree = new KDTree< Segment > ( realInterval );
			this.search = new NearestNeighborSearchOnKDTree< Segment >( kdTree );
			this.realRandomAccessible = Views.interpolate( search, factory );
			this.randomAccessible = Views.raster( realRandomAccessible );
		}
	}

	public void renderVoronoi( final PolygonRoi r, final Img< FloatType > img, final int targetArea )
	{
		// a mask of all pixels within the area (also the area)
		final int[][] mask = makeMask( img, r );
		final int area = mask.length;
		final int numPoints = area / targetArea;
		
		System.out.println( "Area = " + area );
		System.out.println( "TargetArea = " + targetArea );
		System.out.println( "#Points = " + numPoints );

		// for displaying
		ImagePlus imp = new ImagePlus( "voronoi", Alignment.wrap( img ) );
		imp.setDisplayRange( 0, targetArea * 2 );
		imp.show();

		// create an IterableRealInterval and a HashMap that maps the segment id to the location of the sample
		final HashMap< Integer, RealPoint > locations = new HashMap< Integer, RealPoint >();
		final Search search = new Search( createRandomPoints( img, numPoints, r, locations ) );

		Random rnd = new Random( 1353 );

		// rememeber for each segment which pixels are part of it
		final boolean storePixels = true;

		Error errorMetricArea = new QuadraticError();
		Error errorMetricCirc = new CircularityError();

		// initial compute areas
		update( mask, search, storePixels );

		// initial compute simple statistics
		final double targetCircle = 0;
		double errorArea = errorMetricArea.computeError( search.realInterval, targetArea );
		double errorCirc = errorMetricCirc.computeError( search.realInterval, targetCircle );

		int iteration = 0;
		do
		{
			++iteration;
			
			final boolean optArea;
			
			if ( iteration % 4 != 0 )
				optArea = true;
			else
				optArea = false;
			
			Segment next;
			String s = "";
			int knearest;

			if ( optArea )
			{
				knearest = 5;

				if ( iteration % 3 == 0 )
				{
					next = randomSegment( search.realInterval, rnd );
					s += "Random (area)";
				}
				else if ( iteration % 3 == 1 )
				{
					next = smallestSegment( search.realInterval );
					s += "Smallest";
				}
				else
				{
					next = largestSegment( search.realInterval );
					s += "Largest";
				}
			}
			else
			{
				knearest = 5;

				if ( iteration % 3 == 0 )
				{
					next = randomSegment( search.realInterval, rnd );
					s += "Random (circ)";
				}
				else
				{
					next = maxInvCircularSegment( search.realInterval );
					s += "MaxInvCirc";
				}
			}

			s += " (area=" + next.area() + ", " + Util.printCoordinates( locations.get( next.id() ) ) + ", circularity^-1=" + next.invCircularity() + ")";

			// select a close neighbor to the smallest, largest or random segment
			next = neighborSegment( locations.get( next.id() ), search.kdTree, knearest, rnd );

			s += " SELECTED: (area=" + next.area() + ", " + Util.printCoordinates( locations.get( next.id() ) ) + ", circularity^-1=" + next.invCircularity() + ")";

			//System.out.println( s );

			// try to change the largest or the smallest
			final RealPoint p = locations.get( next.id() );

			final double xo = p.getDoublePosition( 0 );
			final double yo = p.getDoublePosition( 1 );

			/*
			double minError, target;
			Error e;
			boolean storePixelsSearch;

			if ( optArea )
			{
				minError = errorArea;
				e = errorMetricArea;
				storePixelsSearch = false;
				target = targetArea;
			}
			else
			{
				minError = errorCirc;
				e = errorMetricCirc;
				storePixelsSearch = true;
				target = targetCircle;
			}
			*/
			
			double minError = errorArea + 300*errorCirc;

			double bestX = xo;
			double bestY = yo;

			double[] dist;
			
			if ( iteration < 1000 )
				dist = new double[]{ -96, -64, -32, -16, -8, -4, -2, -1, 1, 2, 4, 8, 16, 32, 64, 96 };
			else
				dist = new double[]{ -1, -0.75, -0.5, -0.25, -0.1, 0.1, 0.25, 0.5, 0.75, 1 };

			for ( int i = 0; i < dist.length; ++i )
				for ( int dir = 0; dir <= 1; ++dir )
				{
					double x = 0;
					double y = 0;
					
					if ( dir == 0 )
						x = dist[ i ];
					else
						y = dist[ i ];
					
					double newX = xo + x;
					double newY = yo + y;
					
					//if ( r.contains( newX, newY ) )
					{
						p.setPosition( newX, 0 );
						p.setPosition( newY, 1 );

						update( mask, search, true );

						final double errorA = errorMetricArea.computeError( search.realInterval, targetArea );
						final double errorC = errorMetricCirc.computeError( search.realInterval, targetCircle );
						final double errorTest = errorA + 300*errorC;

						if ( errorTest < minError )
						{
							minError = errorTest;
							bestX = newX;
							bestY = newY;
						}
					}
				}

			p.setPosition( bestX, 0 );
			p.setPosition( bestY, 1 );

			// errorArea = minError;

			update( mask, search, storePixels );

			errorArea = errorMetricArea.computeError( search.realInterval, targetArea );
			errorCirc = errorMetricCirc.computeError( search.realInterval, targetCircle );

			//System.out.println( "NEW: (area=" + next.area() + ", " + Util.printCoordinates( locations.get( next.id() ) ) );
			//System.out.println( "Area=" + errorArea + " Circ=" + errorCirc );
			
			System.out.println( iteration + "\t" + errorArea + "\t" + errorCirc + "\t" + minError + "\t" + smallestSegment( search.realInterval ).area() + "\t" + largestSegment( search.realInterval ).area() );
			
			// update the drawing
			drawArea( mask, search.randomAccessible, img );
			imp.updateAndDraw();
		}
		while ( errorArea > 0 );

	}

	final private static Segment maxInvCircularSegment( final Iterable< Segment > segmentMap )
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

	final private static Segment smallestSegment( final Iterable< Segment > segmentMap )
	{
		Segment min = segmentMap.iterator().next();

		for ( final Segment s : segmentMap )
			if ( s.area() < min.area )
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
	final private static Segment neighborSegment( final RealPoint location, final KDTree< Segment > segmentTree, final int k, final Random rnd )
	{
		final KNearestNeighborSearchOnKDTree< Segment > s = new KNearestNeighborSearchOnKDTree< Segment >( segmentTree, k );
		s.search( location );
		return s.getSampler( rnd.nextInt( k ) ).get();
	}

	final private static Segment largestSegment( final Iterable< Segment > segmentMap )
	{
		Segment max = segmentMap.iterator().next();

		for ( final Segment s : segmentMap )
			if ( s.area() > max.area )
				max = s;

		return max;
	}

	final private static Segment randomSegment( final Iterable< Segment > segmentMap, final Random rnd )
	{
		final ArrayList< Segment > l = new ArrayList<Segment>();
		
		for ( final Segment s : segmentMap )
			l.add( s );

		return l.get( rnd.nextInt( l.size() ) );
	}

	final private static void update( final int[][] mask, final Search search, final boolean storePixels )
	{
		// update the new coordinates for the pointlist
		search.update();

		final RandomAccess< Segment > ra = search.randomAccessible.randomAccess();

		for ( final Segment s : search.realInterval )
		{
			s.setArea( 0 );

			if ( storePixels )
				s.pixels().clear();
		}

		for ( final int[] ml : mask )
		{
			ra.setPosition( ml );
			ra.get().incArea();

			if ( storePixels )
				ra.get().pixels().add( ml );
		}
	}

	final protected static void drawId( final int[][] mask, RandomAccessible< Segment > randomAccessible, final RandomAccessible< FloatType > img )
	{
		final RandomAccess< Segment > ra = randomAccessible.randomAccess();
		final RandomAccess< FloatType > ri = img.randomAccess();
	
		for ( final int[] ml : mask )
		{
			ra.setPosition( ml );
			ri.setPosition( ml );

			ri.get().set( ra.get().id() );
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

	public static int[][] makeMask( final Interval interval, final PolygonRoi r )
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


	public static RealPointSampleList< Segment > createRandomPoints( RealInterval interval, int numPoints, final PolygonRoi r, final HashMap< Integer, RealPoint > locations )
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
	
				if ( r == null || r.contains( Math.round( point.getFloatPosition( 0 ) ), Math.round( point.getFloatPosition( 1 ) ) ) )
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

	public static List< PolygonRoi > loadROIs( final List< File > segmentFiles )
	{
		final ArrayList< PolygonRoi > segments = new ArrayList< PolygonRoi >();
		final Opener o = new Opener();

		for ( final File file : segmentFiles )
			segments.add( (PolygonRoi)o.openRoi( file.getAbsolutePath() ) );

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

	public static void main( String[] args )
	{
		new ImageJ();

		final File roiDirectory = new File( "SegmentedWingTemplate" );
		final File wingFile = new File( "A12_002.aligned.zip" );

		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final Img< FloatType > wing = Alignment.convert( wingImp, 0 );
		final Img< FloatType > gene = Alignment.convert( wingImp, 1 );

		final List< PolygonRoi > segments = loadROIs( assembleSegments( roiDirectory ) );

		new Tesselation( wing, gene, segments );
	}
}
