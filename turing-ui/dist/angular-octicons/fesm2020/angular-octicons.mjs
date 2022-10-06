import * as i0 from '@angular/core';
import { Directive, Input, NgModule } from '@angular/core';
import * as octicons from '@primer/octicons';

class OcticonDirective {
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

class OcticonsModule {
}
OcticonsModule.ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "14.2.5", ngImport: i0, type: OcticonsModule, deps: [], target: i0.ɵɵFactoryTarget.NgModule });
OcticonsModule.ɵmod = i0.ɵɵngDeclareNgModule({ minVersion: "14.0.0", version: "14.2.5", ngImport: i0, type: OcticonsModule, declarations: [OcticonDirective], exports: [OcticonDirective] });
OcticonsModule.ɵinj = i0.ɵɵngDeclareInjector({ minVersion: "12.0.0", version: "14.2.5", ngImport: i0, type: OcticonsModule });
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "14.2.5", ngImport: i0, type: OcticonsModule, decorators: [{
            type: NgModule,
            args: [{
                    declarations: [OcticonDirective],
                    imports: [],
                    exports: [OcticonDirective]
                }]
        }] });

/*
 * Public API Surface of angular-octicons
 */

/**
 * Generated bundle index. Do not edit.
 */

export { OcticonDirective, OcticonsModule };
//# sourceMappingURL=angular-octicons.mjs.map
