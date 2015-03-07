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
	final ImagePlus impArea, impId;
	final Img< FloatType > imgArea, imgId;

	public DisplayTesselation( final Interval interval, final List< Roi > segments, final List< File > currentState, final boolean normalizeIds )
	{
		final int targetArea = 200;

		this.imgArea = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
		this.imgId = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );

		this.impArea = new ImagePlus( "voronoiArea", Alignment.wrap( imgArea ) );
		impArea.setDisplayRange( 0, targetArea * 2 );

		this.impId = new ImagePlus( "voronoiId", Alignment.wrap( imgId ) );
		impId.setDisplayRange( 0, targetArea * 2 );

		for ( int i = 0; i < segments.size(); ++i )
		{
			final TesselationThread tt = new TesselationThread( i, segments.get( i ), imgArea, targetArea, currentState.get( i ) );
			Tesselation.drawArea( tt.mask(), tt.search().randomAccessible, imgArea );

			if ( normalizeIds )
				Tesselation.drawId( tt.mask(), tt.search().randomAccessible, imgId, tt.search().realInterval );
			else
				Tesselation.drawId( tt.mask(), tt.search().randomAccessible, imgId );
		}

		impArea.updateAndDraw();
		impId.updateAndDraw();
	}

	public ImagePlus impArea() { return impArea; }
	public ImagePlus impId() { return impId; }
	public Img< FloatType > imgArea() { return imgArea; }
	public Img< FloatType > imgId() { return imgId; }

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
			currentState.add( new File( "movie_localglobal_local/segment_" + i + ".points.txt" ) );

		final DisplayTesselation dt = new DisplayTesselation( new FinalInterval( wingImp.getWidth(), wingImp.getHeight() ), segments, currentState, true );

		dt.impArea().show();
		dt.impId().resetDisplayRange();
		dt.impId().show();
	}

}
