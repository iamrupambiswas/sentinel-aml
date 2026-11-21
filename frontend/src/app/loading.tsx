/** Shown instantly while a page's server data loads: the layout stays, the content shimmers. */
export default function Loading() {
  return (
    <div aria-busy="true" aria-label="Loading">
      <div className="skeleton mb-2 h-3 w-24" />
      <div className="skeleton mb-8 h-7 w-72" />
      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="card p-5">
            <div className="skeleton mb-3 h-3 w-20" />
            <div className="skeleton h-7 w-16" />
          </div>
        ))}
      </div>
      <div className="card p-4">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="flex items-center gap-4 border-b border-line py-3 last:border-0">
            <div className="skeleton h-6 w-11" />
            <div className="flex-1">
              <div className="skeleton mb-2 h-3 w-1/3" />
              <div className="skeleton h-3 w-2/3" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
