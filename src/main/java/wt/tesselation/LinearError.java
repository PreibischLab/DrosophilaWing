package wt.tesselation;


public class LinearError implements Error
{
	@Override
	public double computeError( final Iterable< Segment > segmentMap, final double target )
	{
		double error = 0;

		for ( final Segment s : segmentMap )
		{
			final int area = s.area();
			if ( s.area() < 1 )
				error += Math.abs( 100 * target );
			else
				error += Math.abs( area - target );
		}

		return error;
	}
}
