package wt.quantify;

import fiji.util.gui.GenericDialogPlus;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import java.io.File;

import wt.tesselation.TesselationTools;

public class DisplayTesselation_ImageJ implements PlugIn
{
	@Override
	public void run( final String arg )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Quantify Gene Expression for Directory of aligned Images" );

		gd.addDirectoryField( "Tesselation_data_directory", QuantifyGeneExpression_ImageJ.defaultTesselationDir, 60 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		QuantifyGeneExpression_ImageJ.defaultTesselationDir = gd.getNextString();

		final File tesselationDir = new File( QuantifyGeneExpression_ImageJ.defaultTesselationDir );

		if ( !tesselationDir.exists() )
			throw new RuntimeException( "Tesselation directory '" + tesselationDir.getAbsolutePath() + "' does not exist." );

		if ( !tesselationDir.isDirectory() )
			throw new RuntimeException( "Tesselation directory '" + tesselationDir.getAbsolutePath() + "' is not a directory." );

		final QuantifyGeneExpression qge = new QuantifyGeneExpression( tesselationDir );

		final ImagePlus impId = qge.tesselation().impId( true );
		TesselationTools.drawRealPoint( impId, qge.centerOfMasses() );
		impId.updateAndDraw();
		impId.show();
	}

	public static void main( String[] args )
	{
		new ImageJ();
		new DisplayTesselation_ImageJ().run( null );
	}
}
