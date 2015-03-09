package wt.quantify;

import wt.tesselation.Segment;
import wt.tesselation.TesselationThread;

public class SegmentMeasure extends Segment
{
	final TesselationThread t;

	public SegmentMeasure( final Segment s, final TesselationThread t )
	{
		super( s.id(), s.area(), s.value(), s.pixels() );

		this.t = t;
	}

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

}
