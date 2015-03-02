package wt.tesselation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import wt.Alignment;

public class DisplayTesselation
{
	public DisplayTesselation( final Interval interval, final List< Roi > segments, final List< File > currentState )
	{
		final int targetArea = 200;

		final Img< FloatType > imgArea = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
		final Img< FloatType > imgId = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );

		final ImagePlus impArea = new ImagePlus( "voronoiArea", Alignment.wrap( imgArea ) );
		impArea.setDisplayRange( 0, targetArea * 2 );
		impArea.show();

		final ImagePlus impId = new ImagePlus( "voronoiId", Alignment.wrap( imgId ) );
		impId.setDisplayRange( 0, targetArea * 2 );
		impId.show();

		for ( int i = 0; i < segments.size(); ++i )
		{
			final TesselationThread tt = new TesselationThread( i, segments.get( i ), imgArea, targetArea, currentState.get( i ) );
			Tesselation.drawArea( tt.mask(), tt.search().randomAccessible, imgArea );
			Tesselation.drawId( tt.mask(), tt.search().randomAccessible, imgId, tt.search().realInterval );
		}

		impArea.updateAndDraw();
		impId.updateAndDraw();
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final File roiDirectory = new File( "SegmentedWingTemplate" );
		final File wingFile = new File( "wing_template_A13_2014_01_31.tif" );

		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final List< Roi > segments = Tesselation.loadROIs( Tesselation.assembleSegments( roiDirectory ) );

		// load existing state
		final List< File > currentState = new ArrayList< File >();
		for ( int i = 0; i < segments.size(); ++i )
			currentState.add( new File( "segment_" + i + ".points.txt" ) );

		new DisplayTesselation( new FinalInterval( wingImp.getWidth(), wingImp.getHeight() ), segments, currentState );
	}

}
