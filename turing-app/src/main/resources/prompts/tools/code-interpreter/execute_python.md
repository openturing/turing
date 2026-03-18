Executes Python code and returns the output (stdout and stderr).
Use this tool when the user asks you to:
- Solve math or statistical problems
- Process, analyze, or transform data (CSV, JSON, Excel)
- Generate charts or graphs (use matplotlib/seaborn, save to 'output.png')
- Perform calculations, conversions, or simulations
- Run any computation that benefits from actual code execution

The code runs in a temporary sandbox directory. Any files generated (charts, CSVs, etc.) are saved there and accessible via URL.

IMPORTANT:
- This runs as a standard Python script, NOT a Jupyter notebook. Bare expressions (e.g. a variable name alone on the last line) produce NO output. You MUST always use print() explicitly to produce output. WRONG: result    CORRECT: print(result)
- For charts: use plt.savefig('output.png', dpi=150, bbox_inches='tight') then print the filename. Do NOT call plt.show().
- For data files: save to the current directory and print the filename.
- ALWAYS use descriptive filenames with the CORRECT extension for the format: .xlsx for Excel, .csv for CSV, .pdf for PDF, .json for JSON, .png for images, etc. WRONG: f.txt, output.txt, data.txt for an Excel file. CORRECT: report.xlsx, sales_data.xlsx, analysis.csv. The filename must match the actual file format being written.
- Common libraries available: math, json, csv, datetime, os, sys, re, statistics, collections, itertools, functools, urllib.
- Libraries that MAY be available (if installed): numpy, pandas, matplotlib, seaborn, scipy, openpyxl, requests, Pillow.
- Python version is 3.12+.

INSTALLING MISSING LIBRARIES:
- If a library is missing (ModuleNotFoundError or ImportError), install it BEFORE running the actual code by calling execute_python with an install script:
  import subprocess, sys
  subprocess.check_call([sys.executable, "-m", "pip", "install", "matplotlib", "numpy"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
- Always use sys.executable so pip installs into the same interpreter.
- You may install multiple packages in a single call: ["matplotlib", "numpy", "pandas", "seaborn", "scipy", "openpyxl", "Pillow"].
- After a successful install call execute_python again with the real code.
- If pip itself fails (no internet, permissions), report the error and suggest a stdlib-only alternative when possible.

CHART STYLING (mandatory for all charts):
- Always start chart code with: plt.style.use('seaborn-v0_8-darkgrid')
- Remove top and right spines: ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
- Use colorblind-friendly palette: ['#0072B2', '#D55E00', '#009E73', '#CC79A7', '#F0E442', '#56B4E9', '#E69F00']
- Set figure background to transparent or white: fig.patch.set_facecolor('white')
- Use tight_layout() before savefig for clean margins.

DISPLAYING GENERATED FILES:
- The tool output contains a "Markdown:" line with the EXACT markdown to use. COPY IT VERBATIM.
- URLs are RELATIVE paths starting with /api/v2/code-interpreter/... (e.g., /api/v2/code-interpreter/abc123/output.png)
- CRITICAL: NEVER add "https://", "http://", "sandbox:", or any domain prefix to the URL. The URL must start with / (slash).
  WRONG: https://api.v2.code-interpreter/abc123/output.png
  WRONG: http://localhost:2700/api/v2/code-interpreter/abc123/output.png
  CORRECT: /api/v2/code-interpreter/abc123/output.png
- For IMAGE files: ![description](/api/v2/code-interpreter/SESSION/filename.png)
- For NON-IMAGE files: [Download filename](/api/v2/code-interpreter/SESSION/filename.xlsx)

ERROR HANDLING:
- If the code fails (non-zero exit code) or produces warnings/errors in stderr, the output will include the error details.
- When you receive a ModuleNotFoundError, run the pip install step first, then retry with the real code. Count install + retry as a single attempt.
- For other errors, analyze and fix the code, then call execute_python again. You may retry up to 3 times before giving up.
- Common fixes: missing imports, deprecated APIs, syntax errors.

Args:
    code (str): Python code to execute. Required.
Returns:
    The stdout output of the code, any stderr messages, and URLs for generated files.
