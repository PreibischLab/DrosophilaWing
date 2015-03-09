package wt.quantify;

import ij.ImageJ;

import java.io.File;

import wt.tesselation.LoadTesselation;

public class QuantifyGeneExpression
{
	/**
	 * The tesselation is constant for all registered images to be analyzed
	 */
	final private LoadTesselation tesselation;

	public QuantifyGeneExpression( final File roiDirectory )
	{
		this.tesselation = new LoadTesselation( roiDirectory );
	}

	public void measure( final File alignedImage )
	{
		
	}

	public LoadTesselation tesselation() { return tesselation; }

	public static void main( String[] args )
	{
		new ImageJ();
		QuantifyGeneExpression qge = new QuantifyGeneExpression( new File( "SegmentedWingTemplate" ) );

		qge.tesselation().impId( true ).show();
	}
}
