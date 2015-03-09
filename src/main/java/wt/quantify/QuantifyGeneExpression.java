package wt.quantify;

import ij.ImageJ;

import java.io.File;

import wt.tesselation.LoadTesselation;

public class QuantifyGeneExpression
{
	final LoadTesselation tesselation;

	public QuantifyGeneExpression( final File roiDirectory )
	{
		this.tesselation = new LoadTesselation( roiDirectory );
	}

	public static void main( String[] args )
	{
		new ImageJ();
		QuantifyGeneExpression qge = new QuantifyGeneExpression( new File( "SegmentedWingTemplate" ) );

		qge.tesselation.impId( true ).show();
	}
}
