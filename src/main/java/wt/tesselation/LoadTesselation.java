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
import wt.alignment.ImageTools;

public class LoadTesselation
{
	final Interval interval;
	final List< Roi > segments;
	final int targetArea;
	final ArrayList< TesselationThread > threads;

	ImagePlus impArea, impId;
	Img< FloatType > imgArea, imgId;

	public LoadTesselation( final File roiData )
	{
		this(
				TesselationTools.templateDimensions( roiData ),
				TesselationTools.loadROIs( TesselationTools.assembleSegments( roiData ) ),
				TesselationTools.assemblePoints( roiData ),
				TesselationTools.targetArea( roiData ) );
	}

	public LoadTesselation( final Interval interval, final List< Roi > segments, final List< File > currentState, final int targetArea )
	{
		if ( interval == null )
			throw new RuntimeException( "Interval is null, error loading it?" );

		if ( segments == null )
			throw new RuntimeException( "ROI segment list is null, error loading it?" );

		if ( currentState == null )
			throw new RuntimeException( "Files with tesselation are missing, error loading it?" );

		if ( targetArea < 0 )
			throw new RuntimeException( "Target area for individual segments could not be read." );

		this.interval = interval;
		this.segments = segments;
		this.targetArea = targetArea;

		this.threads = new ArrayList< TesselationThread >();
		for ( int i = 0; i < segments.size(); ++i )
			this.threads.add( new TesselationThread( i, segments.get( i ), interval, targetArea, currentState.get( i ) ) );
	}

	public List< TesselationThread > tesselations() { return threads; }
	public Interval interval() { return interval; }

	public void renderIdImage( final boolean normalizeIds )
	{
		if ( this.imgId == null || this.impId == null )
		{
			this.imgId = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );
			this.impId = new ImagePlus( "voronoiId", ImageTools.wrap( imgId ) );
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
		this.impArea = new ImagePlus( "voronoiArea", ImageTools.wrap( imgArea ) );
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
