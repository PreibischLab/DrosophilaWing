package wt.quantify;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.Checkbox;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spim.fiji.plugin.util.GUIHelper;
import wt.alignment.Alignment_ImageJ;
import wt.tools.CommonFileName;

public class QuantifyGeneExpression_ImageJ implements PlugIn
{
	public static String defaultTesselationDir = "/Users/preibischs/Documents/Drosophila Wing Gompel/SegmentedWingTemplate";
	public static boolean defaultDisplayFileNames = true;
	public static boolean defaultDisplayTesselation = true;
	public static boolean defaultDisplayStack = true;
	public static boolean defaultSaveStack = true;
	public static int defaultNumNeighbors = 5;
	public static double defaultMinValue = 3;

	@Override
	public void run( final String arg )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Quantify Gene Expression for Directory of aligned Images" );

		gd.addDirectoryField( "Tesselation_data_directory", defaultTesselationDir, 60 );
		gd.addDirectoryField( "Directory_with_image_files", Alignment_ImageJ.defaultPath, 60 );
		gd.addCheckbox( "Select_images_from_detected_aligned images in directory", defaultDisplayFileNames );
		gd.addMessage( "" );
		gd.addSlider( "Smoothing (#neighbor tiles)", 0, 20, defaultNumNeighbors );
		gd.addNumericField( "Min_intensity of a peak", defaultMinValue, 1 );
		gd.addMessage( "" );
		gd.addCheckbox( "Display_quantified_images", defaultDisplayStack );
		gd.addCheckbox( "Save_quantified_images", defaultSaveStack );
		gd.addMessage( "" );
		gd.addCheckbox( "Display_tesselation (for debug)", defaultDisplayTesselation );
		gd.addMessage( "" );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		defaultTesselationDir = gd.getNextString();
		Alignment_ImageJ.defaultPath = gd.getNextString();
		defaultDisplayFileNames = gd.getNextBoolean();
		defaultNumNeighbors = (int)Math.round( gd.getNextNumber() );
		defaultDisplayStack = gd.getNextBoolean();
		defaultSaveStack = gd.getNextBoolean();
		defaultDisplayTesselation = gd.getNextBoolean();

		if ( !new File( defaultTesselationDir ).exists() )
		{
			IJ.log( "Tesselation directory '" + new File( defaultTesselationDir ).getAbsolutePath() + "' does not exist." );
			return;
		}

		List< String > alignedImages = CommonFileName.getAlignedImages( new File( Alignment_ImageJ.defaultPath ) );

		if ( defaultDisplayFileNames )
		{
			final GenericDialog gdDisp = new GenericDialog( "Found aligned images" );

			for ( final String image : alignedImages )
			{
				gdDisp.addCheckbox( image, true );

				// otherwise underscores are gone ...
				((Checkbox)gdDisp.getCheckboxes().lastElement()).setLabel( image );
			}

			GUIHelper.addScrollBars( gdDisp );

			gdDisp.showDialog();

			if ( gdDisp.wasCanceled() )
				return;

			List< String > newAlignedImages = new ArrayList< String >();
			for ( final String image : alignedImages )
				if ( gdDisp.getNextBoolean() )
					newAlignedImages.add( image );

			alignedImages = newAlignedImages;
		}

		IJ.log( "Processing following aligned image: " );

		for ( final String image : alignedImages )
			IJ.log( image );

		try
		{
			IJ.log( "num neighbors = " + defaultNumNeighbors );
			IJ.log( "min value = " + defaultMinValue );
			QuantifyGeneExpression.process( new File( defaultTesselationDir ), new File( Alignment_ImageJ.defaultPath ), alignedImages, defaultNumNeighbors, (float)defaultMinValue, defaultDisplayTesselation, defaultDisplayStack, defaultSaveStack );
		}
		catch ( Exception e)
		{
			IJ.log( "Failed to quantify directory '': " + e );
			e.printStackTrace();
		}

	}

	public static void main( String args[] )
	{
		new ImageJ();
		new QuantifyGeneExpression_ImageJ().run( null );
	}
}
