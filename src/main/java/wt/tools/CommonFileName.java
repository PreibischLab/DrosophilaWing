package wt.tools;

import ij.IJ;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class CommonFileName
{
	public static List< Pair< String, String > > pairedImages( final File dir )
	{
		if ( dir == null || !dir.exists() )
		{
			IJ.log( "Provided path '" + dir.getAbsolutePath() + "'is does not exist." );
			return null;
		}

		if ( !dir.isDirectory() )
		{
			IJ.log( "Provided path '" + dir.getAbsolutePath() + "'is not a directory." );
			return null;
		}

		final HashSet< String > files = new HashSet< String >();
		final String[] fileList = dir.list();

		for ( final String f : fileList )
			files.add( f );

		final List< Pair< String, String > > pairs = new ArrayList< Pair< String, String > >();

		for ( final String file : fileList )
		{
			// not removed as checked or partner yet
			if ( files.contains( file ) )
			{
				files.remove( file );

				if ( file.toLowerCase().endsWith( ".tif" ) )
				{
					final String start = file.toLowerCase().substring( 0, file.indexOf( ".tif" ) );

					Pair< String, String > pair = null;

					for ( final String partnerFile : files )
					{
						if (
							partnerFile.toLowerCase().startsWith( start ) &&
							partnerFile.toLowerCase().endsWith( ".tif" ) &&
							partnerFile.charAt( start.length() ) == '_' )
						{
							pair = new ValuePair< String, String >( file, partnerFile );
							break;
						}
					}

					pairs.add( pair );
					files.remove( pair.getB() );
				}
			}
		}

		return pairs;
	}
}
