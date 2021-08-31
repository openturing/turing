import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSEInstance } from '../../model/se-instance.model';
import { NotifierService } from 'angular-notifier';
import { TurSEInstanceService } from '../../service/se-instance.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurSEVendor } from 'src/se/model/se-vendor.model';
import { TurSEVendorService } from 'src/se/service/se-vendor.service';
import { TurLocale } from 'src/locale/model/locale.model';
import { TurLocaleService } from 'src/locale/service/locale.service';
import { FormControl, Validators } from '@angular/forms';

@Component({
  selector: 'se-instance-page',
  templateUrl: './se-instance-page.component.html'
})
export class TurSEInstancePageComponent implements OnInit {
  @ViewChild('modalDelete') modalDelete: ElementRef;
  private turSEInstance: Observable<TurSEInstance>;
  private turLocales: Observable<TurLocale[]>;
  private turSEVendors: Observable<TurSEVendor[]>;

  portControl = new FormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turSEInstanceService: TurSEInstanceService,
    private turLocaleService: TurLocaleService,
    private turSEVendorService: TurSEVendorService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turSEVendors = turSEVendorService.query()
    this.turLocales = turLocaleService.query()
    let id = this.activatedRoute.snapshot.paramMap.get('id');
    this.turSEInstance = this.turSEInstanceService.get(id);

  }

  getTurSEInstance(): Observable<TurSEInstance> {
    return this.turSEInstance;
  }

  getTurSEVendors(): Observable<TurSEVendor[]> {

    return this.turSEVendors;
  }


  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  ngOnInit(): void {
  }

  public save(_turSEInstance: TurSEInstance) {
    this.turSEInstanceService.save(_turSEInstance).subscribe(
      (turSEInstance: TurSEInstance) => {
        _turSEInstance = turSEInstance;
        this.notifier.notify("success", turSEInstance.title.concat(" SE instance was updated."));
      },
      response => {
        this.notifier.notify("error", "SE instance was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turSEInstance: TurSEInstance) {
    this.turSEInstanceService.delete(_turSEInstance).subscribe(
      (turSEInstance: TurSEInstance) => {
        this.notifier.notify("success", turSEInstance.title.concat(" SE instance was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/console/se/instance']);
      },
      response => {
        this.notifier.notify("error", "SE instance was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }
}
