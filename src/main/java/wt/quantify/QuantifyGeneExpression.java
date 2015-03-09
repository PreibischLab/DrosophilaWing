package wt.quantify;

import ij.ImageJ;
import ij.ImagePlus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import wt.alignment.ImageTools;
import wt.tesselation.LoadTesselation;
import wt.tesselation.Segment;
import wt.tesselation.TesselationThread;
import wt.tesselation.TesselationTools;

public class QuantifyGeneExpression
{
	/**
	 * The tesselation is constant for all registered images to be analyzed
	 */
	final private LoadTesselation tesselation;

	final private List< TesselationThread > tesselations;

	final private List< SegmentMeasure > segments;

	public QuantifyGeneExpression( final File roiDirectory )
	{
		this.tesselation = new LoadTesselation( roiDirectory );
		this.tesselations = tesselation.tesselations();
		this.segments = new ArrayList< SegmentMeasure >();

		for ( final TesselationThread t : tesselations )
			for ( final Segment s : t.search().segments() )
				this.segments.add( new SegmentMeasure( s, t ) );
	}

	public void measure( final File alignedImage )
	{
		final Img< FloatType > gene = ImageTools.convert( new ImagePlus( alignedImage.getAbsolutePath() ), 1 );

	}

	public LoadTesselation tesselation() { return tesselation; }

	public Collection< RealPoint > centerOfMasses()
	{
		final ArrayList< RealPoint > list = new ArrayList< RealPoint >();

		for ( final SegmentMeasure s : segments )
			list.add( new RealPoint( s.centerOfMass() ) );

		return list;
	}

	public static void main( String[] args )
	{
		new ImageJ();

		final QuantifyGeneExpression qge = new QuantifyGeneExpression( new File( "SegmentedWingTemplate" ) );

		final ImagePlus impId = qge.tesselation().impId( true );

		TesselationTools.drawRealPoint( impId, qge.centerOfMasses() );
		impId.updateAndDraw();
		impId.show();
	}
}
