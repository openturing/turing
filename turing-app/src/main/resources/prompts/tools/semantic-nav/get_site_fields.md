Retrieves valid facet fields for filtering a specific site.
Call this when you need exact field names for filtering (search_site), comparing (compare_items), date range searches (search_by_date_range), or aggregation (get_aggregated_stats).
Args:
    siteName (str): Name of the site.
Returns:
    A list of field names that can be used for filtering.