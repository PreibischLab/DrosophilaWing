package wt.tessellation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import wt.alignment.ImageTools;

public class TessellationMultiThread
{
	public TessellationMultiThread( final Interval interval, final List< Roi > segments )
	{
		this( interval, segments, null );
	}

	public TessellationMultiThread( final Interval interval, final List< Roi > segments, final List< File > currentState )
	{
		final int targetArea = 200;

		final Img< FloatType > img = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
		final Img< FloatType > imgGlobal = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );

		final ImagePlus imp = new ImagePlus( "voronoi", ImageTools.wrap( img ) );
		imp.setDisplayRange( 0, targetArea * 2 );
		imp.show();

		final ImagePlus impGlobal = new ImagePlus( "voronoiGlobal", ImageTools.wrap( imgGlobal ) );
		impGlobal.show();

		final ArrayList< Pair< TessellationThread, Thread > > threads = new ArrayList< Pair< TessellationThread, Thread > >();

		for ( int i = 0; i < segments.size(); ++i )
		{
			final TessellationThread t;

			if ( currentState == null || currentState.size() != segments.size() )
				t = new TessellationThread( i, segments.get( i ), img, targetArea );
			else
				t = new TessellationThread( i, segments.get( i ), img, targetArea, currentState.get( i ) );

			TessellationTools.drawArea( t.mask(), t.search().randomAccessible, img );
			//TessellationTools.drawRealPoint( imp, t.locationMap().values() );

			threads.add( new ValuePair< TessellationThread, Thread >( t, new Thread( t ) ) );
		}

		imp.updateAndDraw();

		
		// shake?
		/*
		for ( final Pair< TessellationThread, Thread > pair : threads )
		{
			final TessellationThread t = pair.getA();

			System.out.println( t.id() + "\t" + TessellationTools.currentState( t ) );

			t.shake( 1 );

			TessellationTools.drawArea( t.mask(), t.search().randomAccessible, img );
			System.out.println( t.id() + "\t" + TessellationTools.currentState( t ) );
		}
		SimpleMultiThreading.threadWait( 500 );
		imp.updateAndDraw();
		*/

		// expand/shrink?
		/*
		for ( int i = 0; i < 1000; ++i )
		{
			if ( imp.getOverlay() != null )
				imp.getOverlay().clear();

			for ( final Pair< TessellationThread, Thread > pair : threads )
			{
				final TessellationThread t = pair.getA();
	
				t.computeGlobalError( t.numPoints()/15, true, null );
				TessellationTools.drawValue( t.mask(), t.search().randomAccessible, img );

				if ( i == 0 )
					continue;
	
				t.expandShrink( t.numPoints()/15, imp );
	
				//TessellationTools.drawArea( t.mask(), t.search().randomAccessible, img );
				//TessellationTools.drawValue( t.mask(), t.search().randomAccessible, img );
				//System.out.println( t.id() + "\t" + currentState( t ) );
			}

			if ( i == 0 )
				imp.resetDisplayRange();

			imp.updateAndDraw();
			imp.setTitle( "error_iteration_" + i );
			new FileSaver( imp ).saveAsZip( "movie/voronoi_" + i + ".zip" );
			//SimpleMultiThreading.threadHaltUnClean();
			SimpleMultiThreading.threadWait( 100 );
		}

		for ( final Pair< TessellationThread, Thread > pair : threads )
			TessellationTools.writePoints( pair.getA() );

		SimpleMultiThreading.threadHaltUnClean();
		*/

		for ( final Pair< TessellationThread, Thread > pair : threads )
		{
			final TessellationThread t = pair.getA();

			//System.out.println( t.id() + ": Area = " + t.area() );
			//System.out.println( t.id() + ": TargetArea = " + t.targetArea() );
			//System.out.println( t.id() + ": #Points = " + t.numPoints() + "\t" );
			TessellationTools.printCurrentState( t );

			pair.getB().start();
		}

		int currentIteration = 0;

		do
		{
			currentIteration = threads.get( 0 ).getA().iteration();

			// order all threads to run the next iteration
			for ( final Pair< TessellationThread, Thread > pair : threads )
			{
				if ( pair.getA().iteration() != currentIteration )
					throw new RuntimeException( "Iterations out of sync. This should not happen." );

				pair.getA().runNextIteration();
			}

			// wait for threads to finish
			boolean finished = true;
			do
			{

				finished = true;
				for ( final Pair< TessellationThread, Thread > pair : threads )
					finished &= pair.getA().iterationFinished();

				try { Thread.sleep( 10 ); } catch (InterruptedException e) {}
			}
			while ( !finished );

			currentIteration = threads.get( 0 ).getA().iteration();

			boolean anyUpdated = false;

			for ( final Pair< TessellationThread, Thread > pair : threads )
			{
				boolean updated = pair.getA().lastIterationUpdated();
				anyUpdated |= updated;

				if ( updated )
				{
					final TessellationThread t = pair.getA();
					TessellationTools.printCurrentState( t );

					// update the drawing
					t.computeGlobalError( t.numPoints()/15, true, null );
					TessellationTools.drawArea( t.mask(), t.search().randomAccessible, img );
					TessellationTools.drawValue( t.mask(), t.search().randomAccessible, imgGlobal );
					//TessellationTools.drawRealPoint( imp, t.locationMap().values() );
				}
			}

			if ( anyUpdated )
			{
				imp.updateAndDraw();
				impGlobal.updateAndDraw();
				imp.setTitle( "error_iteration_" + currentIteration );
				new FileSaver( imp ).saveAsZip( "movie/voronoi_" + currentIteration + ".zip" );
			}

			if ( currentIteration % 1000 == 0 )
			{
				for ( final Pair< TessellationThread, Thread > pair : threads )
				{
					TessellationTools.writePoints( pair.getA() );

					// write out after every 1000 iterations at least
					pair.getA().logFile().flush();
				}
			}
		}
		while ( currentIteration != -10 );

		for ( final Pair< TessellationThread, Thread > pair : threads )
			pair.getA().logFile().close();
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final File roiDirectory = new File( "SegmentedWingTemplate" );

		final List< Roi > segments = TessellationTools.loadROIs( TessellationTools.assembleSegments( roiDirectory ) );

		// load existing state
		final List< File > currentState = new ArrayList< File >();
		for ( int i = 0; i < segments.size(); ++i )
			currentState.add( new File( "movie_localglobal/segment_" + i + ".points.txt" ) );

		new TessellationMultiThread( TessellationTools.templateDimensions( roiDirectory ), segments, currentState );
	}

}
