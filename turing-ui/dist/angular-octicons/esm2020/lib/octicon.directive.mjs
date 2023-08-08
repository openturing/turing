import { Directive, Input } from '@angular/core';
import * as octicons from '@primer/octicons';
import * as i0 from "@angular/core";
export class OcticonDirective {
    constructor(elem, renderer) {
        this.elem = elem;
        this.renderer = renderer;
    }
    ngOnInit() {
        const el = this.elem.nativeElement;
        el.innerHTML = octicons[this.iconName].toSVG();
        if (this.color) {
            this.renderer.setStyle(el.firstChild, 'fill', this.color);
        }
        if (this.size) {
            this.renderer.setStyle(el.firstChild, 'width', `${this.size}px`);
            this.renderer.setStyle(el.firstChild, 'height', '100%');
        }
    }
}
OcticonDirective.ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "14.2.5", ngImport: i0, type: OcticonDirective, deps: [{ token: i0.ElementRef }, { token: i0.Renderer2 }], target: i0.ɵɵFactoryTarget.Directive });
OcticonDirective.ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "14.0.0", version: "14.2.5", type: OcticonDirective, selector: "[octicon]", inputs: { iconName: ["octicon", "iconName"], color: "color", size: "size" }, ngImport: i0 });
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "14.2.5", ngImport: i0, type: OcticonDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: '[octicon]'
                }]
        }], ctorParameters: function () { return [{ type: i0.ElementRef }, { type: i0.Renderer2 }]; }, propDecorators: { iconName: [{
                type: Input,
                args: ['octicon']
            }], color: [{
                type: Input
            }], size: [{
                type: Input
            }] } });
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoib2N0aWNvbi5kaXJlY3RpdmUuanMiLCJzb3VyY2VSb290IjoiIiwic291cmNlcyI6WyIuLi8uLi8uLi8uLi9wcm9qZWN0cy9hbmd1bGFyLW9jdGljb25zL3NyYy9saWIvb2N0aWNvbi5kaXJlY3RpdmUudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUEsT0FBTyxFQUFFLFNBQVMsRUFBaUMsS0FBSyxFQUFFLE1BQU0sZUFBZSxDQUFDO0FBQ2hGLE9BQU8sS0FBSyxRQUFRLE1BQU0sa0JBQWtCLENBQUM7O0FBTTdDLE1BQU0sT0FBTyxnQkFBZ0I7SUFTM0IsWUFDVSxJQUFnQixFQUNoQixRQUFtQjtRQURuQixTQUFJLEdBQUosSUFBSSxDQUFZO1FBQ2hCLGFBQVEsR0FBUixRQUFRLENBQVc7SUFDekIsQ0FBQztJQUVMLFFBQVE7UUFDTixNQUFNLEVBQUUsR0FBZ0IsSUFBSSxDQUFDLElBQUksQ0FBQyxhQUFhLENBQUM7UUFDaEQsRUFBRSxDQUFDLFNBQVMsR0FBRyxRQUFRLENBQUMsSUFBSSxDQUFDLFFBQVEsQ0FBQyxDQUFDLEtBQUssRUFBRSxDQUFDO1FBRS9DLElBQUksSUFBSSxDQUFDLEtBQUssRUFBRTtZQUNkLElBQUksQ0FBQyxRQUFRLENBQUMsUUFBUSxDQUFDLEVBQUUsQ0FBQyxVQUFVLEVBQUUsTUFBTSxFQUFFLElBQUksQ0FBQyxLQUFLLENBQUMsQ0FBQztTQUMzRDtRQUVELElBQUksSUFBSSxDQUFDLElBQUksRUFBRTtZQUNiLElBQUksQ0FBQyxRQUFRLENBQUMsUUFBUSxDQUFDLEVBQUUsQ0FBQyxVQUFVLEVBQUUsT0FBTyxFQUFFLEdBQUcsSUFBSSxDQUFDLElBQUksSUFBSSxDQUFDLENBQUM7WUFDakUsSUFBSSxDQUFDLFFBQVEsQ0FBQyxRQUFRLENBQUMsRUFBRSxDQUFDLFVBQVUsRUFBRSxRQUFRLEVBQUUsTUFBTSxDQUFDLENBQUM7U0FDekQ7SUFDSCxDQUFDOzs2R0ExQlUsZ0JBQWdCO2lHQUFoQixnQkFBZ0I7MkZBQWhCLGdCQUFnQjtrQkFINUIsU0FBUzttQkFBQztvQkFDVCxRQUFRLEVBQUUsV0FBVztpQkFDdEI7eUhBSUMsUUFBUTtzQkFEUCxLQUFLO3VCQUFDLFNBQVM7Z0JBR2hCLEtBQUs7c0JBREosS0FBSztnQkFHTixJQUFJO3NCQURILEtBQUsiLCJzb3VyY2VzQ29udGVudCI6WyJpbXBvcnQgeyBEaXJlY3RpdmUsIE9uSW5pdCwgRWxlbWVudFJlZiwgUmVuZGVyZXIyLCBJbnB1dCB9IGZyb20gJ0Bhbmd1bGFyL2NvcmUnO1xyXG5pbXBvcnQgKiBhcyBvY3RpY29ucyBmcm9tICdAcHJpbWVyL29jdGljb25zJztcclxuaW1wb3J0IHsgSWNvbk5hbWUgfSBmcm9tICdAcHJpbWVyL29jdGljb25zJztcclxuXHJcbkBEaXJlY3RpdmUoe1xyXG4gIHNlbGVjdG9yOiAnW29jdGljb25dJ1xyXG59KVxyXG5leHBvcnQgY2xhc3MgT2N0aWNvbkRpcmVjdGl2ZSBpbXBsZW1lbnRzIE9uSW5pdCB7XHJcblxyXG4gIEBJbnB1dCgnb2N0aWNvbicpXHJcbiAgaWNvbk5hbWUhOiBJY29uTmFtZTtcclxuICBASW5wdXQoKVxyXG4gIGNvbG9yITogc3RyaW5nO1xyXG4gIEBJbnB1dCgpXHJcbiAgc2l6ZSE6IG51bWJlcjtcclxuXHJcbiAgY29uc3RydWN0b3IoXHJcbiAgICBwcml2YXRlIGVsZW06IEVsZW1lbnRSZWYsXHJcbiAgICBwcml2YXRlIHJlbmRlcmVyOiBSZW5kZXJlcjJcclxuICApIHsgfVxyXG5cclxuICBuZ09uSW5pdCgpIHtcclxuICAgIGNvbnN0IGVsOiBIVE1MRWxlbWVudCA9IHRoaXMuZWxlbS5uYXRpdmVFbGVtZW50O1xyXG4gICAgZWwuaW5uZXJIVE1MID0gb2N0aWNvbnNbdGhpcy5pY29uTmFtZV0udG9TVkcoKTtcclxuXHJcbiAgICBpZiAodGhpcy5jb2xvcikge1xyXG4gICAgICB0aGlzLnJlbmRlcmVyLnNldFN0eWxlKGVsLmZpcnN0Q2hpbGQsICdmaWxsJywgdGhpcy5jb2xvcik7XHJcbiAgICB9XHJcblxyXG4gICAgaWYgKHRoaXMuc2l6ZSkge1xyXG4gICAgICB0aGlzLnJlbmRlcmVyLnNldFN0eWxlKGVsLmZpcnN0Q2hpbGQsICd3aWR0aCcsIGAke3RoaXMuc2l6ZX1weGApO1xyXG4gICAgICB0aGlzLnJlbmRlcmVyLnNldFN0eWxlKGVsLmZpcnN0Q2hpbGQsICdoZWlnaHQnLCAnMTAwJScpO1xyXG4gICAgfVxyXG4gIH1cclxuXHJcblxyXG59XHJcbiJdfQ==