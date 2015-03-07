package wt.tesselation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import wt.alignment.Alignment;

public class LoadTesselation
{
	final Interval interval;
	final List< Roi > segments;
	final int targetArea;
	final ArrayList< TesselationThread> threads;

	ImagePlus impArea, impId;
	Img< FloatType > imgArea, imgId;

	public LoadTesselation( final File roiDirectory )
	{
		this(
				TesselationTools.templateDimensions( roiDirectory ),
				TesselationTools.loadROIs( TesselationTools.assembleSegments( roiDirectory ) ),
				TesselationTools.assemblePoints( roiDirectory ) );
	}

	public LoadTesselation( final Interval interval, final List< Roi > segments, final List< File > currentState )
	{
		this.targetArea = 200;
		this.interval = interval;
		this.segments = segments;

		this.threads = new ArrayList< TesselationThread >();
		for ( int i = 0; i < segments.size(); ++i )
			this.threads.add( new TesselationThread( i, segments.get( i ), interval, targetArea, currentState.get( i ) ) );
	}

	public void renderIdImage( final boolean normalizeIds )
	{
		if ( this.imgId == null || this.impId == null )
		{
			this.imgId = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
			this.impId = new ImagePlus( "voronoiId", Alignment.wrap( imgId ) );
		}

		for ( final TesselationThread tt : threads )
		{
			if ( normalizeIds )
				TesselationTools.drawId( tt.mask(), tt.search().randomAccessible, imgId, tt.search().realInterval );
			else
				TesselationTools.drawId( tt.mask(), tt.search().randomAccessible, imgId );
		}

		this.impId.resetDisplayRange();
		this.impId.updateAndDraw();
	}

	public void renderAreaImage()
	{
		this.imgArea = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
		this.impArea = new ImagePlus( "voronoiArea", Alignment.wrap( imgArea ) );
		this.impArea.setDisplayRange( 0, targetArea * 2 );

		for ( final TesselationThread tt : threads )
			TesselationTools.drawArea( tt.mask(), tt.search().randomAccessible, imgArea );

		impArea.updateAndDraw();
	}

	public ImagePlus impArea()
	{
		if ( impArea == null )
			renderAreaImage();

		return impArea;
	}

	public ImagePlus impId( final boolean normalizeIds )
	{
		if ( impId == null )
			renderIdImage( normalizeIds );

		return impId;
	}

	public Img< FloatType > imgArea()
	{
		if ( imgArea == null )
			renderAreaImage();
		
		return imgArea;
	}

	public Img< FloatType > imgId( final boolean normalizeIds )
	{
		if ( imgId == null )
			renderIdImage( normalizeIds );

		return imgId;
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final LoadTesselation dt = new LoadTesselation( new File( "SegmentedWingTemplate" ) );

		dt.impArea().show();
		dt.impId( true ).show();
	}

}
