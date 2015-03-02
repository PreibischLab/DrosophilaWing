package wt.tesselation.error;

import wt.tesselation.Segment;

public class CircularityError implements Error
{
	

	@Override
	public double computeError( final Iterable< Segment > segmentMap, final double target )
	{
		double error = 0;

		for ( final Segment s : segmentMap )
		{
			final double circ = s.invCircularity();
			
			error += ( circ - target ) * ( circ - target );
		}

		return error;
	}
}
