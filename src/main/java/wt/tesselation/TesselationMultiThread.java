package wt.tesselation;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;

import java.io.File;
import java.util.List;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import wt.Alignment;

public class TesselationMultiThread
{
	public TesselationMultiThread( final Interval interval, final List< Roi > segments )
	{
		final int targetArea = 200;

		final Img< FloatType > img = ArrayImgs.floats( interval.dimension( 0 ), interval.dimension( 1 ) );

		final ImagePlus imp = new ImagePlus( "voronoi", Alignment.wrap( img ) );
		imp.setDisplayRange( 0, targetArea * 2 );
		imp.show();

		TesselationThread t = new TesselationThread( 0, segments.get( 0 ), img, targetArea );

		do
		{
			boolean updated = t.runIteration();

			if ( updated )
			{
				System.out.println(
						t.id() + "\t" +
						t.iteration() + "\t" +
						t.errorArea() + "\t" +
						t.errorCirc() + "\t" +
						t.error() + "\t" + 
						Tesselation.smallestSegment( t.pointList() ).area() + "\t" +
						Tesselation.largestSegment( t.pointList() ).area() + "\t" +
						t.lastdDist() + "\t" +
						t.lastDir() + "\t" +
						t.lastdSigma() );
				
				// update the drawing
				Tesselation.drawArea( t.mask(), t.search().randomAccessible, img );
				//Tesselation.drawOverlay( imp, t.locationMap().values() );

				imp.updateAndDraw();
				new FileSaver( imp ).saveAsZip( "movie/voronoi_" + t.iteration() + ".zip" );
			}
		}
		while ( t.error() > 0 );
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final File roiDirectory = new File( "SegmentedWingTemplate" );
		final File wingFile = new File( "A12_002.aligned.zip" );

		final ImagePlus wingImp = new ImagePlus( wingFile.getAbsolutePath() );

		final List< Roi > segments = Tesselation.loadROIs( Tesselation.assembleSegments( roiDirectory ) );

		new TesselationMultiThread( new FinalInterval( wingImp.getWidth(), wingImp.getHeight() ), segments );
	}

}
