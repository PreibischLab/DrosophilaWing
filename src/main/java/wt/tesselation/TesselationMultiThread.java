package wt.tesselation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;

import java.io.File;
import java.util.List;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import wt.Alignment;

public class TesselationMultiThread
{
	public TesselationMultiThread( final Interval interval, final List< Roi > segments )
	{
		
	}
	public static void main( String[] args )
	{
		new ImageJ();

		final File roiDirectory = new File( "SegmentedWingTemplate" );
		final File wingFile = new File( "A12_002.aligned.zip" );

		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final int w = wingImp.getWidth();
		
		final List< Roi > segments = Tesselation.loadROIs( Tesselation.assembleSegments( roiDirectory ) );

		new TesselationMultiThread( new FinalInterval( wingImp.getWidth(), wingImp.getHeight() ), segments );
	}

}
