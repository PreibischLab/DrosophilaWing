package wt.alignment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.util.Pair;
import wt.tools.CommonFileName;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class Alignment_ImageJ implements PlugIn
{
	public static String defaultPath = "/Users/preibischs/Documents/Drosophila Wing Gompel/samples/B16";
	public static String defaultTemplate = "wing_template_A13_2014_01_31.tif";
	public static double defaultImageWeight = 0.5;
	public static boolean defaultDisplay = true;
	public static boolean defaultDisplayStack = true;
	public static boolean defaultSaveStack = true;

	@Override
	public void run( final String arg )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Select Directory" );

		gd.addFileField( "Template_wing_image", defaultTemplate, 60 );
		gd.addDirectoryField( "Directory_with_files", defaultPath, 60 );
		gd.addNumericField( "Image_weight (non-rigid transform)", defaultImageWeight, 2 );
		gd.addCheckbox( "Display_registered_stack (for debug)", defaultDisplayStack );
		gd.addCheckbox( "Save_registered_stack (for debug)", defaultSaveStack );
		gd.addCheckbox( "Display_pairs of filenames for verification", defaultDisplay );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		defaultTemplate = gd.getNextString();
		defaultPath = gd.getNextString();
		defaultImageWeight = gd.getNextNumber();
		defaultDisplayStack = gd.getNextBoolean();
		defaultSaveStack = gd.getNextBoolean();
		defaultDisplay = gd.getNextBoolean();

		if ( !new File( defaultTemplate ).exists() )
		{
			IJ.log( "Template file '" + new File( defaultTemplate ).getAbsolutePath() + "' does not exist." );
			return;
		}

		List< Pair< String, String > > pairs = CommonFileName.pairedImages( new File( defaultPath ) );

		if ( defaultDisplay )
		{
			final GenericDialog gdDisp = new GenericDialog( "Found pairs of images" );

			for ( final Pair< String, String > pair : pairs )
				gdDisp.addCheckbox( pair.getA() + "_<>_" + pair.getB(), true );

			gdDisp.showDialog();

			if ( gdDisp.wasCanceled() )
				return;

			List< Pair< String, String > > newPairs = new ArrayList< Pair< String, String > >();
			for ( final Pair< String, String > pair : pairs )
				if ( gdDisp.getNextBoolean() )
					newPairs.add( pair );

			pairs = newPairs;
		}

		IJ.log( "Processing following pairs: " );

		for ( final Pair< String, String > pair : pairs )
			IJ.log( pair.getA() + " <> " + pair.getB() );

		try
		{
			Alignment.process( new File( defaultTemplate ), new File( defaultPath ), pairs, defaultImageWeight, defaultDisplayStack, defaultSaveStack );
		}
		catch ( Exception e)
		{
			IJ.log( "Failed to align directory '': " + e );
			e.printStackTrace();
		}
	}

	public static void main( String args[] ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		new ImageJ();
		new Alignment_ImageJ().run( null );
	}
}
