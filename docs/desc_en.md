#### Plugin function
Push the pipeline warehouse components or custom warehouse components archived this time to other projects.
#### Plugin parameters
- Component warehouse：
    - This archived component: The component archived this time in the assembly line warehouse.
    - Custom Warehouse Components: Custom Warehouse Components.
- Component to be distributed：
    - Support * wildcard.
    - When the component warehouse is an assembly line warehouse, only the file name needs to be filled in.
    - When the component warehouse is a custom warehouse, you need to fill in the complete file path.
- Target project Select the project you want to upload.
- Repository paths for custom repositories：
  -It can be left blank, and it will be under the custom warehouse of the target project by default
- Widget list undisplay:
    - When distributing the archived components this time to the custom warehouse of the current project, after checking this option, the distributed components will not be displayed in the pipeline component list. avoid duplicate display.