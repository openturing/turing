Role: Act as a Senior Front-end Developer and expert UX Writer.

Task: Refactor the provided form into a segmented interface using shadcn/ui, focusing on User-Friendly copy and a strictly balanced 50/50 layout where the Select occupies the full half-width.

1. UX Writing & Content:

Friendly Language: Rewrite all Labels and Descriptions to be approachable, clear, and helpful.

Clarity: Ensure the Description (positioned immediately below the Label) explains the purpose of the setting.

2. Balanced 50/50 Row Layout (Switch & Select):

The Rule: Each row must be split into two equal halves (50% left, 50% right).

Left Half (50%): A vertical stack (flex flex-col) containing the Label and its Description.

Right Half (50%): >   * Select: The Select Trigger must be w-full to fill 100% of this right half.

Switch: Must be aligned to the far right within this half. Always use Switch; never use Checkboxes.

Implementation: Use a container with flex flex-row items-center w-full. Use w-1/2 for the text block and w-1/2 for the input container (applying flex justify-end for Switch and w-full for Select).

3. Information Architecture (Accordion Pattern):

Group related fields using shadcn Accordion with type="multiple" and defaultValue so all sections are open by default.

Style: border rounded-lg px-6 for each item, with space-y-6 inside the AccordionContent.

4. Action Footer:

Right-align buttons at the bottom:

Save Changes: Use GradientButton (Primary).

Cancel: Use GradientButton with an outline variant (Secondary).

5. Technical Specs:

Strict Vertical Stacking: Each field/row must occupy its own line. No side-by-side elements.

Imports: Use standard @components/ui paths for Accordion, Switch, Select, and GradientButton.