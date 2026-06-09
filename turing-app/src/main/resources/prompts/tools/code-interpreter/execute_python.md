Executes Python code and returns the output (stdout and stderr).

PREFER ```html + Chart.js for charts and visualizations. Only use matplotlib here if the user explicitly asks for Python/matplotlib.

Use this tool when the user asks you to:
- Solve math or statistical problems
- Process, analyze, or transform data (CSV, JSON, Excel)
- Perform calculations, conversions, or simulations
- Run code that benefits from actual execution
- Generate matplotlib charts ONLY if explicitly requested

The code runs in a temporary sandbox. Files generated are accessible via URL.

RULES:
- This is a Python script, NOT Jupyter. You MUST use print() for all output.
- For charts: plt.savefig('output.png', dpi=150, bbox_inches='tight'). Do NOT call plt.show().
- Use descriptive filenames with correct extensions: report.xlsx, analysis.csv, chart.png.
- Python 3.12+. Common libs: math, json, csv, datetime, os, sys, re, statistics.
- Optional libs (if installed): numpy, pandas, matplotlib, seaborn, scipy, openpyxl, Pillow.

MISSING LIBRARIES:
If ModuleNotFoundError, first run:
  import subprocess, sys; subprocess.check_call([sys.executable, "-m", "pip", "install", "PACKAGE"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
Then retry with the real code.

MATPLOTLIB CHART STYLING (only when using matplotlib):
- plt.style.use('seaborn-v0_8-darkgrid')
- Remove top/right spines: ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
- Palette: ['#0072B2','#D55E00','#009E73','#CC79A7','#F0E442','#56B4E9','#E69F00']
- fig.patch.set_facecolor('white'), plt.tight_layout() before savefig.
Note: This palette is for matplotlib only. Chart.js uses a different palette (see render.md).

DISPLAYING FILES:
The tool output includes ready-to-use markdown. Copy it VERBATIM — do not modify the URL.
Generated-file URLs use the `sandbox:` scheme (e.g. `sandbox:/api/v2/code-interpreter/...`).
Keep the `sandbox:` prefix exactly as given; do NOT strip it, rewrite it, or add a domain.
The chat client resolves `sandbox:` to the real file location automatically.

ERROR HANDLING:
- On ModuleNotFoundError: install first, then retry (counts as one attempt).
- On other errors: fix and retry, up to 3 times.

Args:
    code (str): Python code to execute. Required.
Returns:
    stdout, stderr, and markdown for any generated files.
