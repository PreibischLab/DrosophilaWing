package wt.tesselation.error;

import wt.tesselation.Segment;


public interface Error
{
	public double computeError( final Iterable< Segment > segmentMap, final double target );
}
