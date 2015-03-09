package wt.alignment;

import java.awt.geom.AffineTransform;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import mpicbg.models.AbstractAffineModel2D;
import mpicbg.models.AffineModel2D;
import mpicbg.spim.io.TextFileAccess;
import bunwarpj.Transformation;


public class LoadSaveTransformation
{
	final private boolean mirror;
	final private long[] offset;
	final private AffineModel2D model;
	final private int intervals, subsampling;
	final private double[][] cx, cy;

	private LoadSaveTransformation( final boolean mirror, final long[] offset, final AffineModel2D model, final int intervals, final double[][] cx, final double[][] cy, final int subsampling )
	{
		this.mirror = mirror;
		this.offset = offset;
		this.model = model;
		this.intervals = intervals;
		this.cx = cx;
		this.cy = cy;
		this.subsampling = subsampling;
	}

	public boolean mirror() { return mirror; }
	public long[] offset() { return offset; }
	public AffineModel2D model() { return model; }
	public int intervals() { return intervals; }
	public double[][] cx() { return cx; }
	public double[][] cy() { return cy; }
	public int subsampling() { return subsampling; }

	public static LoadSaveTransformation load( final File file )
	{
		try
		{
			final BufferedReader in = TextFileAccess.openFileReadEx( file );
			String[] line;

			// read mirror
			do{} while ( !in.readLine().trim().toLowerCase().startsWith( "mirror:" ) );
			final boolean mirror = Boolean.parseBoolean( in.readLine().trim() );

			// read offset
			final long[] offset = new long[ 2 ];
			do{} while ( !in.readLine().trim().toLowerCase().startsWith( "offset:" ) );
			line = in.readLine().trim().split( "\t" );
			offset[ 0 ] = Long.parseLong( line[ 0 ] );
			offset[ 1 ] = Long.parseLong( line[ 0 ] );

			// read affine
			final AffineModel2D model = new AffineModel2D();
			do{} while ( !in.readLine().trim().toLowerCase().startsWith( "affine:" ) );
			final float m00 = Float.parseFloat( in.readLine().trim().split( "\t" )[ 1 ] );
			final float m01 = Float.parseFloat( in.readLine().trim().split( "\t" )[ 1 ] );
			final float m02 = Float.parseFloat( in.readLine().trim().split( "\t" )[ 1 ] );
			final float m10 = Float.parseFloat( in.readLine().trim().split( "\t" )[ 1 ] );
			final float m11 = Float.parseFloat( in.readLine().trim().split( "\t" )[ 1 ] );
			final float m12 = Float.parseFloat( in.readLine().trim().split( "\t" )[ 1 ] );
			model.set( m00, m10, m01, m11, m02, m12 );

			// read nonrigid
			do{} while ( !in.readLine().trim().toLowerCase().startsWith( "nonrigid:" ) );
			final int subsampling = Integer.parseInt( in.readLine().trim().split( "\t" )[ 1 ] );
			final int intervals = Integer.parseInt( in.readLine().trim().split( "\t" )[ 1 ] );
			final double[][] cx = new double[ intervals + 3 ][ intervals + 3 ];
			final double[][] cy = new double[ intervals + 3 ][ intervals + 3 ];
			if ( !loadTransformation( in, intervals, cx, cy ) )
			{
				System.out.println( "Failed to load non-rigid alignment." );
				return null;
			}

			return new LoadSaveTransformation( mirror, offset, model, intervals, cx, cy, subsampling );
		}
		catch ( Exception e )
		{
			System.out.println( "Failed to load alignment: " + e );
			e.printStackTrace();
			return null;
		}
	}

	public static boolean save( final String log, final boolean mirror, final long[] offset, final AbstractAffineModel2D< ? > model, final Transformation t, final int subsampling, final File file )
	{
		return save( log, mirror, offset, model, t.getIntervals(), t.getDirectDeformationCoefficientsX(), t.getDirectDeformationCoefficientsY(), subsampling, file );
	}

	public static boolean save( final String log, final boolean mirror, final long[] offset, final AbstractAffineModel2D< ? > model, final int intervals, final double[][] cx, final double[][] cy, final int subsampling, final File file )
	{
		if ( offset == null || offset.length != 2 )
			throw new RuntimeException( "This is two-dimensional only, the offset is not " );

		final AffineTransform at = model.createAffine();

		try
		{
			final PrintWriter w = TextFileAccess.openFileWriteEx( file );

			w.println( "log:");
			w.println( log );
			w.println();

			w.println( "mirror:" );
			w.println( mirror );
			w.println();

			w.println( "offset:" );
			w.println( offset[ 0 ] + "\t" + offset[ 1 ] );
			w.println();

			w.println( "affine:" );
			w.println( "m00\t" + at.getScaleX() );
			w.println( "m01\t" + at.getShearX() );
			w.println( "m02\t" + at.getTranslateX() );
			w.println( "m10\t" + at.getShearY() );
			w.println( "m11\t" + at.getScaleY() );
			w.println( "m12\t" + at.getTranslateY() );
			w.println();

			w.println( "nonrigid:" );
			w.println( "subsampling\t" + subsampling );
			w.println( "intervals\t" + intervals );
			w.println( saveElasticTransformation( intervals, cx, cy ) );
			w.close();

			return true;
		}
		catch ( IOException e )
		{
			System.out.println( "Failed to save alignment: " + e );
			e.printStackTrace();
			return false;
		}
	}

	protected static String saveElasticTransformation(
			final int intervals,
			final double[][] cx,
			final double[][] cy )
	{
		String fw = "X Coeffs -----------------------------------\n";

		for ( int i = 0; i < intervals + 3; ++i )
		{
			for ( int j = 0; j < intervals + 3; ++j )
			{
				String aux = Double.toString( cx[ i ][ j ] );
				while ( aux.length() < 21 )
					aux = " " + aux;
				fw += aux + " ";
			}
			fw += "\n";
		}

		fw += "\nY Coeffs -----------------------------------\n";

		for ( int i = 0; i < intervals + 3; ++i )
		{
			for ( int j = 0; j < intervals + 3; ++j )
			{
				String aux = Double.toString( cy[ i ][ j ] );
				while ( aux.length() < 21 )
					aux = " " + aux;
				fw += aux + " ";
			}
			fw += "\n";
		}

		return fw;
	}

	protected static boolean loadTransformation( final BufferedReader br, final int intervals, final double[][] cx, final double[][] cy )
	{
		try
		{
			// Skip first line
			br.readLine();
			int lineN = 1;

			// Read the cx coefficients
			for ( int i = 0; i < intervals + 3; ++i )
			{
				String line = br.readLine();
				lineN++;
				final StringTokenizer st = new StringTokenizer( line );

				if ( st.countTokens() != intervals + 3 )
				{
					System.out.println( "Line " + lineN + ": Cannot read enough coefficients" );
					return false;
				}

				for ( int j = 0; j < intervals + 3; ++j )
					cx[ i ][ j ] = Double.valueOf( st.nextToken() ).doubleValue();
			}

			// Skip next 2 lines
			br.readLine();
			br.readLine();
			lineN += 2;

			// Read the cy coefficients
			for ( int i = 0; i < intervals + 3; ++i )
			{
				String line = br.readLine();
				lineN++;
				final StringTokenizer st = new StringTokenizer( line );

				if ( st.countTokens() != intervals + 3 )
				{
					System.out.println( "Line " + lineN + ": Cannot read enough coefficients" );
					return false;
				}

				for ( int j = 0; j < intervals + 3; ++j )
					cy[ i ][ j ] = Double.valueOf( st.nextToken() ).doubleValue();
			}

			return true;
		}
		catch (IOException e)
		{
			System.out.println( "IOException exception" + e );
			return false;
		}
		catch (NumberFormatException e)
		{
			System.out.println( "Number format exception" + e );
			return false;
		}
	}
}
