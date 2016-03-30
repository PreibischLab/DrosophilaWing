package wt.alignment;

import java.awt.Checkbox;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spim.fiji.plugin.util.GUIHelper;
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
	public static boolean defaultDisplayFileNames = true;
	public static boolean defaultDisplayStack = true;
	public static boolean defaultSaveStack = true;

	@Override
	public void run( final String arg )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Align Directory of Images" );

		gd.addFileField( "Template_wing_image", defaultTemplate, 60 );
		gd.addDirectoryField( "Directory_with_image_files", defaultPath, 60 );
		gd.addCheckbox( "Select_images_from_detected_pairs of images in directory", defaultDisplayFileNames );
		gd.addMessage( "" );
		gd.addNumericField( "Image_weight (non-rigid transform)", defaultImageWeight, 2 );
		gd.addCheckbox( "Display_registered_stack (for debug)", defaultDisplayStack );
		gd.addCheckbox( "Save_registered_stack (for debug)", defaultSaveStack );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		defaultTemplate = gd.getNextString();
		defaultPath = gd.getNextString();
		defaultDisplayFileNames = gd.getNextBoolean();
		defaultImageWeight = gd.getNextNumber();
		defaultDisplayStack = gd.getNextBoolean();
		defaultSaveStack = gd.getNextBoolean();

		if ( !new File( defaultTemplate ).exists() )
		{
			IJ.log( "Template file '" + new File( defaultTemplate ).getAbsolutePath() + "' does not exist." );
			return;
		}

		List< Pair< String, String > > pairs = CommonFileName.pairedImages( new File( defaultPath ) );

		if ( defaultDisplayFileNames )
		{
			final GenericDialog gdDisp = new GenericDialog( "Found pairs of images" );

			for ( final Pair< String, String > pair : pairs )
			{
				gdDisp.addCheckbox( pair.getA() + "_<>_" + pair.getB(), true );

				// otherwise underscores are gone ...
				((Checkbox)gdDisp.getCheckboxes().lastElement()).setLabel( pair.getA() + "_<>_" + pair.getB() );
			}

			GUIHelper.addScrollBars( gdDisp );

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

		AlignmentProcess currentProcess = new AlignmentProcess();
		currentProcess.setTemplate(   new File( defaultTemplate  ))
					  .setPairs(      new File( defaultPath ), pairs )
					  .setImageWeight( defaultImageWeight )
					  .setShowSummary( defaultDisplayStack )
					  .setSaveSummary( defaultSaveStack );
		try
		{
			currentProcess.run();
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
