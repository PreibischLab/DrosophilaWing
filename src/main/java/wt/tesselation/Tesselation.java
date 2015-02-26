package wt.tesselation;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.io.FileSaver;
import ij.io.Opener;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

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
import wt.Alignment;
import wt.tesselation.error.CircularityError;
import wt.tesselation.error.Error;
import wt.tesselation.error.QuadraticError;
import wt.tesselation.pointupdate.DistancePointUpdater;
import wt.tesselation.pointupdate.PointUpdater;

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

		renderVoronoi( segments.get( 0 ), gene.factory().create( gene, gene.firstElement() ), targetArea );
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
				knearest = 4;

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
				knearest = 4;

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
			/*
			 * 1	1927549.0	720.6955331994182	2143757.6599598257	16	585	96.0	0
				2	1855031.0	722.3261188329318	2071728.8356498796	16	572	-16.0	1
				3	1751531.0	722.1560726261263	1968177.821787838	16	559	-32.0	0
				4	1675501.0	724.5754088593773	1892873.6226578131	16	559	32.0	0
				5	1619769.0	723.9701343373135	1836960.040301194	21	526	16.0	1
				6	1574443.0	729.2870861810584	1793229.1258543176	21	526	32.0	0
				7	1495057.0	743.1429505560124	1717999.8851668036	28	526	32.0	1
				8	1419353.0	731.2564717569448	1638729.9415270835	28	526	-32.0	0
				9	1394025.0	731.772070615251	1613556.6211845754	28	526	-32.0	0
				10	1341887.0	739.4910070763469	1563734.302122904	28	526	32.0	1
			 */
			// 10	1341887.0	739.4910070763469	1563734.302122904	28	526

			// backup all locations
			final ArrayList< RealPoint > backup = new ArrayList< RealPoint >();
			
			for ( final RealPoint p : locations.values() )
				backup.add( new RealPoint( p ) );
			
			// try to change the largest or the smallest
			final RealPoint p = locations.get( next.id() );

			//final PointUpdater updater = new DistancePointUpdater( 20 / Math.max( 1, ( iteration / 100 ) ) );// SimplePointUpdater();

			double minError = errorArea + 300*errorCirc;
			double bestdx = 0;
			double bestdy = 0;
			int bestDir = -1;
			double bestDist = -1;
			double bestSigma = -1;
			
			double[] dist = new double[]{ -64, -32, -16, -8, -4, 4, 8, 16, 32, 64 };
			double[] sigmas = new double[]{ 40, 20, 10, 5, 0 };

			for ( int i = 0; i < dist.length; ++i )
				dist[ i ] /= Math.max( 0.1, ( iteration / (5*100.0) ) );

			for ( int i = 0; i < sigmas.length; ++i )
				sigmas[ i ] /= Math.max( 1, ( iteration / (5*1000.0) ) );

			if ( iteration % (5*100) == 1 )
			{
				String t = "it: " + iteration + ": ";
				for ( int i = 0; i < dist.length; ++i )
					t += dist[ i ] + ", ";
				IJ.log( t );

				t = "it: " + iteration + ": ";
				for ( int i = 0; i < sigmas.length; ++i )
					t += sigmas[ i ] + ", ";
				IJ.log( t );
			}
			//if ( iteration < 1000 )
			//	dist = new double[]{ -96, -64, -32, -16, -8, -4, -2, -1, 1, 2, 4, 8, 16, 32, 64, 96 };
			//else
			//	dist = new double[]{ -1, -0.75, -0.5, -0.25, -0.1, 0.1, 0.25, 0.5, 0.75, 1 };

			
			
			//for ( int si = 0; si < sigmas.length; ++si )
			{
				int si = rnd.nextInt( sigmas.length );
				final PointUpdater updater = new DistancePointUpdater( sigmas[ si ] );

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
	
						update( mask, search, true );
	
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
							bestSigma = sigmas[ si ];
						}
						
						// restore positions
						int j = 0;
						for ( final RealPoint rp : locations.values() )
							rp.setPosition( backup.get( j++ ) );
					}
			}

			if ( bestSigma > 0 )
			{
				final PointUpdater updater = new DistancePointUpdater( bestSigma );
				updater.updatePoints( p, locations.values(), bestdx, bestdy );
			}
			update( mask, search, storePixels );

			errorArea = errorMetricArea.computeError( search.realInterval, targetArea );
			errorCirc = errorMetricCirc.computeError( search.realInterval, targetCircle );

			//System.out.println( "NEW: (area=" + next.area() + ", " + Util.printCoordinates( locations.get( next.id() ) ) );
			//System.out.println( "Area=" + errorArea + " Circ=" + errorCirc );
			
			if ( bestDir != -1 )
			{
				System.out.println( iteration + "\t" + errorArea + "\t" + errorCirc + "\t" + minError + "\t" + smallestSegment( search.realInterval ).area() + "\t" + largestSegment( search.realInterval ).area() + "\t" + bestDist + "\t" + bestDir + "\t" + bestSigma );
			
				// update the drawing
				drawArea( mask, search.randomAccessible, img );
				//drawOverlay( imp, locations.values() );
				imp.updateAndDraw();
				new FileSaver( imp ).saveAsZip( "movie/voronoi_" + iteration + ".zip" );
			}
			
			if ( iteration % 100000 == 0 )
			{
				IJ.log( "iteration: " + iteration + "\t" + errorArea + "\t" + errorCirc + "\t" + minError + "\t" + smallestSegment( search.realInterval ).area() + "\t" + largestSegment( search.realInterval ).area() );
				for ( final RealPoint rp : locations.values() )
					IJ.log( Util.printCoordinates( rp ) );
			}
		}
		while ( errorArea > 0 );

	}

	protected final static void drawOverlay( final ImagePlus imp, final Collection< RealPoint > points )
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
