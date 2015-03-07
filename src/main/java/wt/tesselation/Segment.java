package wt.tesselation;

import java.util.ArrayList;

public class Segment
{
	final static private double PI4 = 4.0 * Math.PI;
	
	final int id;
	int area;
	float value;
	final ArrayList< int[] > pixels  = new ArrayList< int[] >();

	public Segment( final int id )
	{
		this( id, -1 );
	}

	public Segment( final int id, final int area )
	{
		this.id = id;
		this.area = area;
		this.value = 0;
	}

	public ArrayList< int[] > pixels() { return pixels; }
	public int id() { return id; }
	public int area() { return area; }
	public void setArea( final int area ) { this.area = area; }
	public void incArea() { ++area; }
	public float value() { return value; }

	public double[] centerOfMass()
	{
		final double[] c = new double[ 2 ];
		centerOfMass( c );
		return c;
	}

	public void centerOfMass( final double[] c )
	{
		double xp = 0;
		double yp = 0;

		for ( final int[] p : pixels )
		{
			xp += p[ 0 ];
			yp += p[ 1 ];
		}

		xp /= (double)pixels.size();
		yp /= (double)pixels.size();

		c[ 0 ] = xp;
		c[ 1 ] = yp;
	}

	public void setValue( final float value ) { this.value = value; }

	public double invCircularity()
	{
		final double perimeter = borderPixels();
		if ( perimeter == 0 )
			return 100;

		return ( perimeter * perimeter * 2 ) / ( PI4 * area() );
	}

	public int borderPixels()
	{
		int minX, minY, maxX, maxY;

		if ( pixels.size() == 0 )
			return 0;

		minX = maxX = pixels.get( 0 )[ 0 ];
		minY = maxY = pixels.get( 0 )[ 1 ];

		for ( final int[] l : pixels )
		{
			minX = Math.min( minX, l[ 0 ] );
			minY = Math.min( minY, l[ 1 ] );
			maxX = Math.max( maxX, l[ 0 ] );
			maxY = Math.max( maxY, l[ 1 ] );
		}

		final int w = maxX - minX + 1 + 2; // +2: a black border around it for easy 4-neighborhood testing
		final int h = maxY - minY + 1 + 2; // +2: a black border around it for easy 4-neighborhood testing

		// populate the temporary image
		final byte[][] img = new byte[ w ][ h ];

		for ( final int[] l : pixels )
			img[ l[ 0 ] - minX + 1 ][ l[ 1 ] - minY + 1 ] = 1;

		// check for each pixel if it is a border pixel by 4-neighborhood
		int borderPixels = 0;

		for ( final int[] l : pixels )
		{
			final int x = l[ 0 ] - minX + 1;
			final int y = l[ 1 ] - minY + 1;

			if ( img[ x ][ y ] != 1 )
				throw new RuntimeException( "this should be one" );

			if ( img[ x - 1 ][ y ] == 0 || img[ x + 1 ][ y ] == 0 || img[ x ][ y - 1 ] == 0 || img[ x ][ y + 1 ] == 0 )
				++borderPixels;
		}

		return borderPixels;
	}
}
