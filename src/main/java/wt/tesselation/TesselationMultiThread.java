package wt.tesselation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.io.TextFileAccess;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import wt.Alignment;

public class TesselationMultiThread
{
	public TesselationMultiThread( final Interval interval, final List< Roi > segments )
	{
		this( interval, segments, null );
	}

	public TesselationMultiThread( final Interval interval, final List< Roi > segments, final List< File > currentState )
	{
		final int targetArea = 200;

		final Img< FloatType > img = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );

		final ImagePlus imp = new ImagePlus( "voronoi", Alignment.wrap( img ) );
		imp.setDisplayRange( 0, targetArea * 2 );
		imp.show();

		final ArrayList< Pair< TesselationThread, Thread > > threads = new ArrayList< Pair< TesselationThread, Thread > >();

		for ( int i = 0; i < segments.size(); ++i )
		{
			final TesselationThread t;

			if ( currentState == null || currentState.size() != segments.size() )
				t = new TesselationThread( i, segments.get( i ), img, targetArea );
			else
				t = new TesselationThread( i, segments.get( i ), img, targetArea, currentState.get( i ) );

			Tesselation.drawArea( t.mask(), t.search().randomAccessible, img );
			//Tesselation.drawOverlay( imp, t.locationMap().values() );

			threads.add( new ValuePair< TesselationThread, Thread >( t, new Thread( t ) ) );
		}

		/*
		// shake?
		for ( final Pair< TesselationThread, Thread > pair : threads )
		{
			final TesselationThread t = pair.getA();

			t.shake( 2.5 );
			Tesselation.drawArea( t.mask(), t.search().randomAccessible, img );
			Tesselation.drawOverlay( imp, t.locationMap().values() );
			System.out.println( t.id() + "\t" + currentState( t ) );
		}
		*/

		imp.updateAndDraw();

		for ( final Pair< TesselationThread, Thread > pair : threads )
		{
			final TesselationThread t = pair.getA();

			System.out.println( t.id() + ": Area = " + t.area() );
			System.out.println( t.id() + ": TargetArea = " + t.targetArea() );
			System.out.println( t.id() + ": #Points = " + t.numPoints() + "\t" );
			printCurrentState( t );

			pair.getB().start();
		}

		do
		{
			int currentIteration = threads.get( 0 ).getA().iteration();

			// order all threads to run the next iteration
			for ( final Pair< TesselationThread, Thread > pair : threads )
			{
				if ( pair.getA().iteration() != currentIteration )
					throw new RuntimeException( "Iterations out of sync. This should not happen." );

				/*
				if ( currentIteration == 0 )
				{
					System.out.println( "Shrink" );
					pair.getA().setRunExpandShrink( true );
				}
				else
				{
					System.out.println( "Normal" );
					pair.getA().setRunExpandShrink( false );
					SimpleMultiThreading.threadHaltUnClean();
				}
				*/
				pair.getA().runNextIteration();
			}

			// wait for threads to finish
			boolean finished = true;
			do
			{

				finished = true;
				for ( final Pair< TesselationThread, Thread > pair : threads )
					finished &= pair.getA().iterationFinished();

				try { Thread.sleep( 10 ); } catch (InterruptedException e) {}
			}
			while ( !finished );

			currentIteration = threads.get( 0 ).getA().iteration();

			boolean anyUpdated = false;

			for ( final Pair< TesselationThread, Thread > pair : threads )
			{
				boolean updated = pair.getA().lastIterationUpdated();
				anyUpdated |= updated;

				if ( updated )
				{
					final TesselationThread t = pair.getA();
					printCurrentState( t );

					// update the drawing
					Tesselation.drawArea( t.mask(), t.search().randomAccessible, img );
					//Tesselation.drawOverlay( imp, t.locationMap().values() );
				}
			}

			if ( anyUpdated )
			{
				imp.updateAndDraw();
				new FileSaver( imp ).saveAsZip( "movie/voronoi_" + currentIteration + ".zip" );
			}

			if ( currentIteration % 1000 == 0 )
			{
				for ( final Pair< TesselationThread, Thread > pair : threads )
				{
					final PrintWriter out = TextFileAccess.openFileWrite( "segment_" + pair.getA().id() + ".points.txt" );

					for ( final Segment s : pair.getA().search().realInterval )
					{
						final RealPoint p = pair.getA().locationMap().get( s.id() );
						out.println( s.id() + "\t" + p.getDoublePosition( 0 ) + "\t" + p.getDoublePosition( 1 ) );
					}
					
					out.close();

					// write out after every 1000 iterations at least
					pair.getA().logFile().flush();
				}
			}
		}
		while ( true );
	}

	protected String currentState( final TesselationThread t )
	{
		return 
				t.iteration() + "\t" +
				t.errorArea() + "\t" +
				t.errorCirc() + "\t" +
				t.error() + "\t" + 
				Tesselation.smallestSegment( t.pointList() ).area() + "\t" +
				Tesselation.largestSegment( t.pointList() ).area() + "\t" +
				t.lastdDist() + "\t" +
				t.lastDir() + "\t" +
				t.lastdSigma();
	}

	protected void printCurrentState( final TesselationThread t )
	{
		String s = currentState( t );

		t.logFile().println( s );
		System.out.println( t.id() + "\t" + s );
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final File roiDirectory = new File( "SegmentedWingTemplate" );
		final File wingFile = new File( "A12_002.aligned.zip" );

		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final List< Roi > segments = Tesselation.loadROIs( Tesselation.assembleSegments( roiDirectory ) );

		// load existing state
		final List< File > currentState = new ArrayList< File >();
		//for ( int i = 0; i < segments.size(); ++i )
		//	currentState.add( new File( "segment_" + i + ".points.txt" ) );

		new TesselationMultiThread( new FinalInterval( wingImp.getWidth(), wingImp.getHeight() ), segments, currentState );
	}

}
