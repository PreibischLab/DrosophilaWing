package wt.quantify;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import java.io.File;

import wt.tessellation.TessellationTools;

public class DisplayTessellation_ImageJ implements PlugIn
{
	@Override
	public void run( final String arg )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Display Tessellation" );

		gd.addDirectoryField( "Tessellation_data_directory", QuantifyGeneExpression_ImageJ.defaultTessellationDir, 60 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		QuantifyGeneExpression_ImageJ.defaultTessellationDir = gd.getNextString();

		final File tessellationDir = new File( QuantifyGeneExpression_ImageJ.defaultTessellationDir );

		if ( !tessellationDir.exists() )
			throw new RuntimeException( "Tessellation directory '" + tessellationDir.getAbsolutePath() + "' does not exist." );

		if ( !tessellationDir.isDirectory() )
			throw new RuntimeException( "Tessellation directory '" + tessellationDir.getAbsolutePath() + "' is not a directory." );

		final QuantifyGeneExpression qge = new QuantifyGeneExpression( tessellationDir );

		final ImagePlus impId = qge.tessellation().impId( true );
		TessellationTools.drawRealPoint( impId, qge.centerOfMasses() );
		impId.updateAndDraw();
		impId.show();

		IJ.log( "Red circles indicate the center of mass of each segment" );
	}

	public static void main( String[] args )
	{
		new ImageJ();
		new DisplayTessellation_ImageJ().run( null );
	}
}
