package wt.tools;

import ij.IJ;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
			final ZipFile zf = new ZipFile( file );// WARNING: resource leak, zf is never closed
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
			final ZipFile zf = new ZipFile( file ); // WARNING: resource leak, zf is never closed
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

	public static List< Pair< File, File > > pairedImages( final File dir )
	{
		String ext = ".tif";
			
		if ( dir == null || !dir.exists() )
		{
			IJ.log( "Provided path '" + dir.getAbsolutePath() + "'does not exist." );
			return null;
		}

		if ( !dir.isDirectory() )
		{
			IJ.log( "Provided path '" + dir.getAbsolutePath() + "'is not a directory." );
			return null;
		}

		final ArrayList< fileHandler > files     = new ArrayList< fileHandler >();
		final ArrayList< fileHandler > filesFull = new ArrayList< fileHandler >();
		final String[] fileList = dir.list();
		Arrays.sort(fileList);
		
		// filter files 
		for ( final String f : fileList )
		{
			if (f.toLowerCase().endsWith( ext ))
			{
				fileHandler tmp = new fileHandler(f);
				files.add( tmp);
				filesFull.add(tmp);
			}
		}
		final List< Pair< File, File > > pairs = new ArrayList< Pair< File, File > >();
		fileHandler selectedPartner = null;
		for ( final fileHandler file : filesFull )
		{
			// not removed as checked or partner yet
			if ( files.contains( file ) )
			{
				
				Pair< File, File > pair = null;
				if (file.getType().equals("brightfield") || file.getType().equals("")) 
				{ // if no type (or explicit "brightfield" type) -> take as reference
					
					// remove the file because we will know if it has a partner or not
					files.remove( file ); 

					System.out.println("file tested:" + file.getFullName());
					// look for a partner in the list
					for ( final fileHandler partnerFile : files )
					{
						if ( // condition to be a partner
							partnerFile.getID().equals(file.getID()) &&
							!partnerFile.getType().equals(file.getType()) )
							
						{
							System.out.println("      paired!");
							if (pair == null) {
								fileHandler brgtField;
								fileHandler gene;
								if (file.getType().equals("brightfield"))
								{
									if (partnerFile.getType().equals("brightfield"))
									{
										System.out.println( "Be carefull, " + partnerFile.getFullName() +
															" and "+ file.getFullName() +
															" are labeled as brightfield images. Skipping the second one.");
										continue;
									} 
									else
									{
										brgtField = file;
										gene = partnerFile;
									}
									
								} else if (partnerFile.getType().equals("brightfield"))
								{
									gene = file;
									brgtField = partnerFile;
									
								}
								else
								{ // default case (not type = brightfield)
									brgtField = file;
									gene = partnerFile;
								}
									
								selectedPartner = partnerFile;
								pair = new ValuePair< File, File >( new File(dir.getPath(), brgtField.getFullName()),
																    new File(dir.getPath(),      gene.getFullName()) );
							}
							else
								System.out.println( "Be carefull, " + partnerFile.getFullName() +
													" could also be paired width "+ pair.getA() +
													"(chosen match:" + pair.getB() + ")");
						}
					}
	
					// if a pair is found, remove the partner from the list
					if ( pair != null )
					{
						pairs.add( pair );
						files.remove( selectedPartner );
					}
					System.out.println("size:" + files.size());
					
				}
			}
		}
		for (final fileHandler file : files) {
			System.out.println( "no pair found for " + file.getFullName() );
		}
		return pairs;
	}
	
	public static class fileHandler
	{
		private String idSeparator = "_";
		private String name = null;
		private String fullName = null;
		private String ext = "";
		private String id = null;
		private String type = "";
		
		public fileHandler(String file)
		{
			fullName = file;
			
			// finds name
			int i = file.lastIndexOf('.');
			if (i > 0) {
			    ext = file.substring(i+1);
			    name = fullName.substring( 0, i );
			}
			else
			{
				name = fullName;
			}
			
			// find ID ( after the last idSeparator in the name)
			i = name.lastIndexOf(idSeparator);
			if (i > 0)
			{
				id = name.substring( 0, i ).toLowerCase();
				type = name.substring(i+1);
			}
			else
				id = name.toLowerCase();
			
		}
		
		public final String getName() {return name;}
		public final String getFullName() {return fullName;}
		public final String getExt() {return ext;}
		public final String getID() {return id;}
		public final String getType() {return type;}

		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass()) 
				return false;
			
			fileHandler other = (fileHandler) obj;
			if (fullName == null) {
				if (other.fullName != null)
					return false;
			} else if (!fullName.equals(other.fullName))
				return false;
			return true;
		}

		
		
		
	}
	
	

	
	public static String IDFromPair(String file1, String file2)
	{
		int k=0;
		if (file2.isEmpty())
			k = file1.length();
		else
		{
			while (k<file1.length() && k<file2.length())
			{
				if (file1.charAt(k) != file2.charAt(k))
					break;
				k++;
			}
			while (k>0)
			{
				if (file1.charAt(k-1) != '_')
					break;
				k--;
			}
		}
		return file1.substring(0, k);
	}
	
	public static String IDFromPair(File file1, File file2)
	{
		return IDFromPair(file1.getName(), file2.getName());
	}
	
}
