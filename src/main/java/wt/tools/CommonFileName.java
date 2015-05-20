package wt.tools;

import ij.IJ;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class CommonFileName
{
	public interface ZIPFileFilter
	{
		public boolean accept( final ZipEntry ze );
	}

	public static class DefaultZIPFileFilter implements ZIPFileFilter
	{
		public boolean accept( final ZipEntry ze ) { return true; }
	}

	public static BufferedReader readZIP( final File file, final String name )
	{
		try
		{
			final ZipFile zf = new ZipFile( file );
			final Enumeration< ? extends ZipEntry > entries = zf.entries();

			while ( entries.hasMoreElements() )
			{
				final ZipEntry ze = entries.nextElement();
				if ( ze.getName().equals( name ) )
					return new BufferedReader( new InputStreamReader( zf.getInputStream( ze ) ) );
			}

			IJ.log( "Cannot find file '" + name + "' in zipfile '" + file.getAbsolutePath() + "'" );
			return null;
		}
		catch ( Exception e )
		{
			IJ.log( "Cannot find file '" + name + "' in zipfile '" + file.getAbsolutePath() + "'" );
			e.printStackTrace();
			return null;
		}
	}

	public List< ZipEntry > listZIP( final File file )
	{
		return listZIP( file, new DefaultZIPFileFilter() );
	}

	public List< ZipEntry > listZIP( final File file, final ZIPFileFilter filter )
	{
		try
		{
			final ArrayList< ZipEntry > list = new ArrayList< ZipEntry >();
			final ZipFile zf = new ZipFile( file );
			final Enumeration< ? extends ZipEntry > entries = zf.entries();

			while ( entries.hasMoreElements() )
			{
				final ZipEntry ze = entries.nextElement();

				if ( filter.accept( ze ) )
					list.add( ze );
			}

			return list;
		}
		catch ( Exception e )
		{
			IJ.log( "Cannot list in zipfile '" + file.getAbsolutePath() + "'" );
			e.printStackTrace();
			return null;
		}
	}

	public static List< String > getAlignedImages( final File dir )
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

		final String[] names = dir.list( new FilenameFilter()
		{
			@Override
			public boolean accept( final File dir, final String name )
			{
				if ( name.endsWith( ".aligned.zip" ) )
					return true;
				else
					return false;
			}
		} );

		final ArrayList< String > list = new ArrayList< String >();

		for ( final String n : names )
			list.add( n );

		return list;
	}

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

					if ( pair != null )
					{
						pairs.add( pair );
						files.remove( pair.getB() );
					}
				}
			}
		}

		return pairs;
	}
}
