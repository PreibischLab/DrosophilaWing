package wt.tesselation;


public interface Error
{
	public double computeError( final Iterable< Segment > segmentMap, final double target );
}
